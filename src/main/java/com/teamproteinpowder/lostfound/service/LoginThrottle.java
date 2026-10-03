package com.teamproteinpowder.lostfound.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Brute-force protection for the login endpoint.
 *
 * Two independent fixed windows, both counting only failed attempts:
 *
 *   per identifier  5 failures / 15 min  stops guessing one account's password
 *   per client IP  30 failures / 15 min  stops one source spraying a common
 *                                        password across many accounts
 *
 * The identifier window is keyed on the normalised string the client typed,
 * NOT on the user row. Keying on the user would mean only real accounts could
 * ever be locked, so a 429 would itself confirm "this account exists". Keying
 * on the identifier makes a real and a made-up username behave identically.
 *
 * Both windows are checked before any password hashing happens, so a locked
 * client cannot also use the endpoint to burn CPU on PBKDF2.
 *
 * State is in memory. That is deliberate: it needs no schema change, survives
 * nothing an attacker can trigger, and the app already assumes a single
 * instance for sessions. Behind a reverse proxy, configure forwarded headers
 * (server.forward-headers-strategy) or every client shares the proxy's address
 * and the per-IP window applies to all of them together.
 *
 * Trade-off, stated plainly: an attacker who knows a username can keep that
 * account locked by failing five times every fifteen minutes. That is the
 * standard cost of account lockout; the lock is temporary and self-clearing.
 */
@Component
public class LoginThrottle {

    static final int MAX_PER_IDENTIFIER = 5;
    static final int MAX_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Cap on tracked keys. Without it, an attacker submitting random
     * identifiers could grow these maps until the process runs out of memory.
     */
    static final int MAX_TRACKED = 50_000;

    private record Window(Instant start, int failures) {
    }

    private final ConcurrentHashMap<String, Window> identifiers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> addresses = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Reject before doing any work if either window is exhausted. */
    public void checkAllowed(String identifier, String ip) {
        Instant now = clock.instant();
        if (exhausted(identifiers, key(identifier), MAX_PER_IDENTIFIER, now)
                || exhausted(addresses, ip, MAX_PER_IP, now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Try again in 15 minutes");
        }
    }

    public void recordFailure(String identifier, String ip) {
        Instant now = clock.instant();
        bump(identifiers, key(identifier), now);
        bump(addresses, ip, now);
    }

    /**
     * A correct password clears that identifier's window. The address window
     * is left alone, so a sprayer who guesses one account right is not reset.
     */
    public void recordSuccess(String identifier) {
        identifiers.remove(key(identifier));
    }

    // ------------------------------------------------------------------

    private boolean exhausted(ConcurrentHashMap<String, Window> map, String k, int max, Instant now) {
        if (k == null) {
            return false;
        }
        Window w = map.get(k);
        return w != null && !expired(w, now) && w.failures() >= max;
    }

    private void bump(ConcurrentHashMap<String, Window> map, String k, Instant now) {
        if (k == null) {
            return;
        }
        if (map.size() >= MAX_TRACKED) {
            evictExpired(map, now);
        }
        /* compute() is atomic per key, so two concurrent failures cannot both
           read the same count and each write count + 1. */
        map.compute(k, (ignored, w) -> (w == null || expired(w, now))
                ? new Window(now, 1)
                : new Window(w.start(), w.failures() + 1));
    }

    private void evictExpired(ConcurrentHashMap<String, Window> map, Instant now) {
        map.entrySet().removeIf(e -> expired(e.getValue(), now));
        /* Still full of live entries means a flood of distinct keys inside one
           window. Dropping the oldest half bounds memory; the cost is that some
           of those keys get a fresh window, which is far better than an OOM. */
        if (map.size() >= MAX_TRACKED) {
            map.entrySet().stream()
                    .sorted((a, b) -> a.getValue().start().compareTo(b.getValue().start()))
                    .limit(MAX_TRACKED / 2)
                    .map(java.util.Map.Entry::getKey)
                    .toList()
                    .forEach(map::remove);
        }
    }

    private static boolean expired(Window w, Instant now) {
        return w.start().plus(WINDOW).isBefore(now);
    }

    /** "Admin", " admin " and "ADMIN" must share one window. */
    private static String key(String identifier) {
        if (identifier == null) {
            return null;
        }
        String k = identifier.strip().toLowerCase(Locale.ROOT);
        return k.isEmpty() ? null : k;
    }
}
