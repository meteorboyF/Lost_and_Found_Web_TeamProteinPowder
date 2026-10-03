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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for the login brute-force guard. The clock is injected so window
 * expiry can be tested without sleeping for fifteen minutes.
 */
class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private MutableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        throttle = new LoginThrottle(clock);
    }

    private void failTimes(String identifier, String ip, int n) {
        for (int i = 0; i < n; i++) {
            throttle.checkAllowed(identifier, ip);
            throttle.recordFailure(identifier, ip);
        }
    }

    private static void assertLocked(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void allowsExactlyFiveFailuresThenLocks() {
        failTimes("student", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER);
        assertLocked(() -> throttle.checkAllowed("student", "10.0.0.1"));
    }

    @Test
    void lockStillAppliesToALaterCorrectPassword() {
        // The check runs before the password is verified, so being locked out
        // cannot be escaped by finally guessing right.
        failTimes("student", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER);
        assertLocked(() -> throttle.checkAllowed("student", "10.0.0.1"));
    }

    @Test
    void identifierVariantsShareOneWindow() {
        failTimes("Student", "10.0.0.1", 2);
        failTimes(" student ", "10.0.0.2", 2);
        failTimes("STUDENT", "10.0.0.3", 1);
        assertLocked(() -> throttle.checkAllowed("student", "10.0.0.4"));
    }

    @Test
    void unknownAndKnownIdentifiersAreThrottledIdentically() {
        // The throttle never sees whether an account exists, so it cannot leak it.
        failTimes("real-user", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER);
        failTimes("made-up-user", "10.0.0.2", LoginThrottle.MAX_PER_IDENTIFIER);
        assertLocked(() -> throttle.checkAllowed("real-user", "10.0.0.1"));
        assertLocked(() -> throttle.checkAllowed("made-up-user", "10.0.0.2"));
    }

    @Test
    void lockExpiresAfterTheWindow() {
        failTimes("student", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER);
        assertLocked(() -> throttle.checkAllowed("student", "10.0.0.1"));

        clock.advance(LoginThrottle.WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> throttle.checkAllowed("student", "10.0.0.1"));
    }

    @Test
    void successClearsTheIdentifierButNotTheAddress() {
        failTimes("student", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER - 1);
        throttle.recordSuccess("student");
        // Identifier is fresh again: four more failures still don't lock it.
        failTimes("student", "10.0.0.9", LoginThrottle.MAX_PER_IDENTIFIER - 1);
        assertDoesNotThrow(() -> throttle.checkAllowed("student", "10.0.0.9"));
    }

    @Test
    void oneAddressSprayingManyAccountsIsLocked() {
        // Password spraying: one source, a different account every time.
        for (int i = 0; i < LoginThrottle.MAX_PER_IP; i++) {
            throttle.checkAllowed("user-" + i, "203.0.113.7");
            throttle.recordFailure("user-" + i, "203.0.113.7");
        }
        assertLocked(() -> throttle.checkAllowed("fresh-account", "203.0.113.7"));
        // A different address is unaffected.
        assertDoesNotThrow(() -> throttle.checkAllowed("fresh-account", "198.51.100.1"));
    }

    @Test
    void lockingOneAccountDoesNotAffectAnother() {
        failTimes("student", "10.0.0.1", LoginThrottle.MAX_PER_IDENTIFIER);
        assertDoesNotThrow(() -> throttle.checkAllowed("admin", "10.0.0.2"));
    }

    @Test
    void blankIdentifiersAreIgnoredRatherThanSharingAWindow() {
        // Otherwise every empty submission would land in one shared bucket.
        failTimes("", "10.0.0.1", 10);
        failTimes("   ", "10.0.0.2", 10);
        assertDoesNotThrow(() -> throttle.checkAllowed("", "10.0.0.3"));
    }

    @Test
    void concurrentFailuresAreAllCounted() throws Exception {
        // compute() must be atomic: no lost updates under contention.
        int threads = 40;
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    throttle.recordFailure("target", "10.0.0." + Thread.currentThread().threadId() % 250);
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertLocked(() -> throttle.checkAllowed("target", "10.9.9.9"));
    }
}
