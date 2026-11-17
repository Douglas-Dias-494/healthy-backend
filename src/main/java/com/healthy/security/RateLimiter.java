package com.healthy.security;

import com.healthy.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter kept in memory: state is per instance and lost on restart.
 * With several servers, move it to Redis (or use the reverse proxy's limiter).
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Duration KEEP_FOR = Duration.ofHours(2);

    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void record(String key) {
        Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(clock.instant());
        }
        if (events.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
    }

    public void requireBelow(String key, int limit, Duration window) {
        Deque<Instant> deque = events.get(key);
        if (deque == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(now.minus(window))) {
                deque.removeFirst();
            }
            if (deque.size() >= limit) {
                long retryAfter = Duration.between(now, deque.peekFirst().plus(window)).toSeconds() + 1;
                throw new TooManyRequestsException(Math.max(1, retryAfter));
            }
        }
    }

    public void clear(String key) {
        events.remove(key);
    }

    public void clearAll() {
        events.clear();
    }

    private void cleanup() {
        Instant limit = clock.instant().minus(KEEP_FOR);
        events.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                return deque.isEmpty() || deque.peekLast().isBefore(limit);
            }
        });
    }
}
