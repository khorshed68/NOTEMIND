package com.notemind.security;

import com.notemind.config.AppProperties;
import com.notemind.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "notemind";

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        String secret = properties.jwt().secret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        String lowered = secret.toLowerCase(Locale.ROOT);
        if (bytes.length < 32 || lowered.contains("replace-with") || lowered.contains("change-me")) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a random value of at least 32 bytes (openssl rand -base64 48)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlSeconds = properties.jwt().accessTtl().toSeconds();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<UUID> verify(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
