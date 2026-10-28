package com.idxintelligence.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts attempts per key (an email or a client address) in a sliding window, in memory. One backend
 * instance serves the site, so this is enough to slow down password guessing and email flooding.
 */
class AttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** True while the key has room for another attempt in the window. */
    boolean allowed(String key) {
        Deque<Instant> list = attempts.get(key);
        if (list == null) return true;
        synchronized (list) {
            prune(list);
            return list.size() < maxAttempts;
        }
    }

    void record(String key) {
        Deque<Instant> list = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list);
            list.addLast(clock.instant());
        }
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    void clear(String key) {
        attempts.remove(key);
    }

    private void prune(Deque<Instant> list) {
        Instant cutoff = clock.instant().minus(window);
        while (!list.isEmpty() && list.peekFirst().isBefore(cutoff)) list.pollFirst();
    }
}
