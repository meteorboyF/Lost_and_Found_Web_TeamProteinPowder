package com.teamproteinpowder.lostfound.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private static final RateLimiter.Policy THREE_PER_MINUTE =
            new RateLimiter.Policy("test", 3, Duration.ofMinutes(1));

    private MutableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new RateLimiter(clock);
    }

    private static void assertLimited(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void allowsExactlyTheLimitThenRejects() {
        for (int i = 0; i < 3; i++) {
            limiter.consume(THREE_PER_MINUTE, "1.2.3.4");
        }
        assertLimited(() -> limiter.consume(THREE_PER_MINUTE, "1.2.3.4"));
    }

    @Test
    void windowResetsAfterItExpires() {
        for (int i = 0; i < 3; i++) {
            limiter.consume(THREE_PER_MINUTE, "1.2.3.4");
        }
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertDoesNotThrow(() -> limiter.consume(THREE_PER_MINUTE, "1.2.3.4"));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.consume(THREE_PER_MINUTE, "1.2.3.4");
        }
        assertDoesNotThrow(() -> limiter.consume(THREE_PER_MINUTE, "5.6.7.8"));
    }

    @Test
    void policiesAreIndependentForTheSameKey() {
        // Hitting the comment limit must not block the same address from signing up.
        for (int i = 0; i < RateLimiter.GUEST_COMMENT.max(); i++) {
            limiter.consume(RateLimiter.GUEST_COMMENT, "1.2.3.4");
        }
        assertLimited(() -> limiter.consume(RateLimiter.GUEST_COMMENT, "1.2.3.4"));
        assertDoesNotThrow(() -> limiter.consume(RateLimiter.REGISTRATION, "1.2.3.4"));
    }

    @Test
    void rejectionMessageSaysWhenToRetry() {
        for (int i = 0; i < 3; i++) {
            limiter.consume(THREE_PER_MINUTE, "1.2.3.4");
        }
        clock.advance(Duration.ofSeconds(20));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> limiter.consume(THREE_PER_MINUTE, "1.2.3.4"));
        assertEquals("Too many requests. Try again in 40 seconds", ex.getReason());
    }

    @Test
    void blankKeysAreNotPooledIntoOneSharedBucket() {
        for (int i = 0; i < 10; i++) {
            limiter.consume(THREE_PER_MINUTE, "");
            limiter.consume(THREE_PER_MINUTE, null);
        }
        assertDoesNotThrow(() -> limiter.consume(THREE_PER_MINUTE, "   "));
    }

    @Test
    void aSimultaneousBurstLetsThroughExactlyTheLimit() throws Exception {
        // The increment and the check are one atomic step. If they weren't,
        // a burst could all read "under the limit" and every request would pass.
        int threads = 50;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    try {
                        limiter.consume(THREE_PER_MINUTE, "burst");
                        allowed.incrementAndGet();
                    } catch (ResponseStatusException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertEquals(3, allowed.get(), "exactly the limit may pass");
        assertEquals(threads - 3, rejected.get());
    }
}
