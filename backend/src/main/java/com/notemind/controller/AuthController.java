package com.notemind.controller;

import com.notemind.config.AppProperties;
import com.notemind.dto.AuthDtos.AuthResponse;
import com.notemind.dto.AuthDtos.ChangePasswordRequest;
import com.notemind.dto.AuthDtos.IssuedSession;
import com.notemind.dto.AuthDtos.LoginRequest;
import com.notemind.dto.AuthDtos.RegisterRequest;
import com.notemind.dto.AuthDtos.UpdateProfileRequest;
import com.notemind.dto.AuthDtos.UserDto;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String COOKIE = "nm_refresh";

    private final AuthService auth;
    private final AppProperties.Jwt jwtProperties;

    public AuthController(AuthService auth, AppProperties properties) {
        this.auth = auth;
        this.jwtProperties = properties.jwt();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return withCookie(auth.register(request), response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return withCookie(auth.login(request), response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = COOKIE, required = false) String token,
                                HttpServletResponse response) {
        return withCookie(auth.refresh(token), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletResponse response) {
        auth.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.me(user.id());
    }

    @PutMapping("/me")
    public UserDto updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(user.id(), request);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(user.id(), request);
    }

    private AuthResponse withCookie(IssuedSession session, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), jwtProperties.refreshTtl()));
        return session.response();
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
