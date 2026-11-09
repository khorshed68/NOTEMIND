package com.notemind.security;

import com.notemind.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RateLimiter limiter;
    private final AppProperties.RateLimit limits;

    public RateLimitInterceptor(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.limits = properties.rateLimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            limiter.check("auth:" + request.getRemoteAddr(), limits.authPerMinute(), Duration.ofMinutes(1),
                    "Too many sign-in attempts. Please wait a minute and try again.");
        } else if (path.equals("/api/documents")) {
            limiter.check("upload:" + principalKey(request), limits.uploadsPerHour(), Duration.ofHours(1),
                    "Upload limit reached. Please try again later.");
        } else if (matcher.match("/api/chats/*/messages", path) || matcher.match("/api/chats/*/messages/stream", path)
                || matcher.match("/api/documents/*/quiz", path) || matcher.match("/api/courses/*/quiz", path)) {
            limiter.check("ai:" + principalKey(request), limits.aiPerMinute(), Duration.ofMinutes(1),
                    "You are sending requests too quickly. Please wait a moment.");
        }
        return true;
    }

    private String principalKey(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user
                ? user.id().toString()
                : request.getRemoteAddr();
    }
}
