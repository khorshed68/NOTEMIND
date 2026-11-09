package com.notemind.security;

import com.notemind.exception.RateLimitExceededException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory token bucket. Suitable for the single-instance deployment in compose.yaml;
 * a horizontally scaled deployment would move this state to Redis behind the same method.
 */
@Component
public class RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String key, int capacity, Duration window, String message) {
        long retryAfter = buckets.computeIfAbsent(key, k -> new Bucket(capacity, window)).tryConsume();
        if (retryAfter > 0) {
            throw new RateLimitExceededException(message, retryAfter);
        }
    }

    @Scheduled(fixedDelay = 600_000)
    void evictIdle() {
        long now = System.nanoTime();
        buckets.values().removeIf(bucket -> bucket.isFull(now));
    }

    private static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefill = System.nanoTime();

        Bucket(int capacity, Duration window) {
            this.capacity = capacity;
            this.refillPerNano = capacity / (double) window.toNanos();
            this.tokens = capacity;
        }

        /** @return 0 when a token was taken, otherwise whole seconds until the next token. */
        synchronized long tryConsume() {
            refill(System.nanoTime());
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000_000d));
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
        }
    }
}
