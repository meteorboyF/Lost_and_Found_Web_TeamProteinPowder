package com.teamproteinpowder.lostfound.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fixed-window rate limiting for endpoints anyone can call: registration and
 * public comments.
 *
 * Unlike {@link LoginThrottle}, which counts only failures, this counts every
 * request — a flood of successful comments is exactly the abuse being stopped.
 *
 * Limits are deliberately generous. This is a university app, so many students
 * share one campus NAT address; a tight per-address limit would lock out a whole
 * lecture hall registering at once. The aim is to make abuse slow and visible,
 * not to make a busy campus network look like an attacker.
 *
 * In memory, single-instance, capped in size — the same assumptions and the
 * same proxy caveat as LoginThrottle.
 */
@Component
public class RateLimiter {

    /** A named limit: at most {@code max} requests per {@code window}. */
    public record Policy(String name, int max, Duration window) {
    }

    /** Signup also answers "is this email registered?", so it bounds enumeration too. */
    public static final Policy REGISTRATION = new Policy("register", 20, Duration.ofHours(1));

    /** Guests are identified only by address. */
    public static final Policy GUEST_COMMENT = new Policy("comment-guest", 10, Duration.ofMinutes(10));

    /** Signed-in members are keyed on their account, so a shared address doesn't matter. */
    public static final Policy MEMBER_COMMENT = new Policy("comment-member", 30, Duration.ofMinutes(10));

    static final int MAX_TRACKED = 50_000;

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Count one request against {@code key} under {@code policy}, or reject it.
     *
     * The count is incremented and checked in one atomic compute(), so a burst
     * of simultaneous requests cannot all read "under the limit" and pass.
     */
    public void consume(Policy policy, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        String k = policy.name() + '|' + key;

        if (windows.size() >= MAX_TRACKED) {
            evict(now);
        }

        Window w = windows.compute(k, (ignored, old) ->
                (old == null || expired(old, policy, now))
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));

        if (w.count() > policy.max()) {
            long retryAfter = Math.max(1, Duration.between(now, w.start().plus(policy.window())).toSeconds());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + humanise(retryAfter));
        }
    }

    private static boolean expired(Window w, Policy p, Instant now) {
        return w.start().plus(p.window()).isBefore(now);
    }

    /** Drop windows older than the longest policy; if still full, drop the oldest half. */
    private void evict(Instant now) {
        Duration longest = REGISTRATION.window();
        windows.entrySet().removeIf(e -> e.getValue().start().plus(longest).isBefore(now));
        if (windows.size() >= MAX_TRACKED) {
            windows.entrySet().stream()
                    .sorted((a, b) -> a.getValue().start().compareTo(b.getValue().start()))
                    .limit(MAX_TRACKED / 2)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(windows::remove);
        }
    }

    private static String humanise(long seconds) {
        if (seconds < 90) {
            return seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
