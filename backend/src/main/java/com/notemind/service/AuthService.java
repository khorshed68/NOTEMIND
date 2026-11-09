package com.notemind.service;

import com.notemind.config.AppProperties;
import com.notemind.dto.AuthDtos.AuthResponse;
import com.notemind.dto.AuthDtos.ChangePasswordRequest;
import com.notemind.dto.AuthDtos.IssuedSession;
import com.notemind.dto.AuthDtos.LoginRequest;
import com.notemind.dto.AuthDtos.RegisterRequest;
import com.notemind.dto.AuthDtos.UpdateProfileRequest;
import com.notemind.dto.AuthDtos.UserDto;
import com.notemind.entity.Enums.Role;
import com.notemind.entity.RefreshToken;
import com.notemind.entity.User;
import com.notemind.exception.ApiException;
import com.notemind.repository.RefreshTokenRepository;
import com.notemind.repository.UserRepository;
import com.notemind.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Incorrect email or password.";
    private static final String INVALID_SESSION = "Your session has expired. Please sign in again.";
    /** Concurrent refreshes (two tabs) present the same token moments apart; that is not theft. */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();
    /** Compared against when the email is unknown so response time does not reveal registered addresses. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt,
                       AppProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtl = properties.jwt().refreshTtl();
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public IssuedSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(Role.STUDENT);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        return startSession(user);
    }

    @Transactional
    public IssuedSession login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = encoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been disabled. Please contact an administrator.");
        }
        return startSession(user);
    }

    /** Rotates the refresh token. Replaying an old token after the grace window revokes every session. */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        RefreshToken token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        Instant now = Instant.now();
        if (token.getRevokedAt() != null && token.getRevokedAt().plus(ROTATION_GRACE).isBefore(now)) {
            tokens.revokeAllForUser(token.getUserId(), now);
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        User user = users.findById(token.getUserId()).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(now);
        }
        return startSession(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        return UserDto.from(loadUser(userId));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = loadUser(userId);
        user.setName(request.name().trim());
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = loadUser(userId);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        tokens.revokeAllForUser(userId, Instant.now());
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 60_000)
    public void purgeExpiredTokens() {
        tokens.deleteExpired(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private IssuedSession startSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(refreshTtl));
        tokens.save(token);

        return new IssuedSession(new AuthResponse(jwt.issue(user), jwt.ttlSeconds(), UserDto.from(user)), raw);
    }

    private User loadUser(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
