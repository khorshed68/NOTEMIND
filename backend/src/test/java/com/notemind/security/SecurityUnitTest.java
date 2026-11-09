package com.notemind.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notemind.config.AppProperties;
import com.notemind.entity.User;
import com.notemind.exception.RateLimitExceededException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityUnitTest {

    private static AppProperties props(String secret, Duration ttl, int aiPerMinute) {
        return new AppProperties(new AppProperties.Jwt(secret, ttl, Duration.ofDays(14), false),
                new AppProperties.Cors(List.of()), new AppProperties.Storage("x"), new AppProperties.Upload(1, 1),
                new AppProperties.Processing(1),
                new AppProperties.Rag(384, 32, 900, 150, 5, 30, 0.35, 0.75, 0.4, 8, 6),
                new AppProperties.RateLimit(aiPerMinute, 1, 1),
                new AppProperties.Ocr(false, "t", "e", 200, 25), new AppProperties.Admin("", "", ""));
    }

    private static final String SECRET = "a-long-enough-random-looking-secret-value-1234";

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void tokenRoundTrips() {
        JwtService service = new JwtService(props(SECRET, Duration.ofMinutes(5), 1));
        User user = user();
        assertThat(service.verify(service.issue(user))).contains(user.getId());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(props(SECRET, Duration.ofSeconds(-120), 1));
        assertThat(service.verify(service.issue(user()))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService a = new JwtService(props(SECRET, Duration.ofMinutes(5), 1));
        JwtService b = new JwtService(props(SECRET + "-different", Duration.ofMinutes(5), 1));
        assertThat(b.verify(a.issue(user()))).isEmpty();
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        JwtService service = new JwtService(props(SECRET, Duration.ofMinutes(5), 1));
        String token = service.issue(user());
        assertThat(service.verify(token.substring(0, token.length() - 3) + "abc")).isEmpty();
        assertThat(service.verify("garbage")).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void weakOrPlaceholderSecretsPreventStartup() {
        for (String secret : List.of("short", "replace-with-at-least-32-random-bytes-please", "change-me-change-me-change-me-change-me")) {
            assertThatThrownBy(() -> new JwtService(props(secret, Duration.ofMinutes(5), 1)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rateLimiterBlocksAfterCapacityAndIsPerKey() {
        RateLimiter limiter = new RateLimiter();
        limiter.check("a", 2, Duration.ofHours(1), "m");
        limiter.check("a", 2, Duration.ofHours(1), "m");

        assertThatThrownBy(() -> limiter.check("a", 2, Duration.ofHours(1), "limit"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("limit")
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).retryAfterSeconds()).isPositive());
        assertThatCode(() -> limiter.check("b", 2, Duration.ofHours(1), "m")).doesNotThrowAnyException();
    }

    @Test
    void interceptorLimitsAiRequestsButNotReads() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiter(), props(SECRET, Duration.ofMinutes(5), 1));
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/chats/abc/messages/stream");
        interceptor.preHandle(post, new MockHttpServletResponse(), new Object());

        assertThatThrownBy(() -> interceptor.preHandle(post, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(RateLimitExceededException.class);

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/chats/abc/messages");
        assertThat(interceptor.preHandle(read, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(read, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void loginAttemptsAreLimitedPerAddress() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiter(), props(SECRET, Duration.ofMinutes(5), 5));
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");
        interceptor.preHandle(login, new MockHttpServletResponse(), new Object());

        assertThatThrownBy(() -> interceptor.preHandle(login, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
