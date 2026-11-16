package com.healthy.security;

import com.healthy.common.TooManyRequestsException;
import com.healthy.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Teste unitário (sem Spring): o relógio é controlado, então "esperar 15 minutos" é instantâneo. */
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private MutableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-16T12:00:00Z"));
        limiter = new RateLimiter(clock);
    }

    @Test
    void allowsAttemptsBelowTheLimit() {
        for (int i = 0; i < 4; i++) {
            limiter.record("k");
        }
        assertThatCode(() -> limiter.requireBelow("k", 5, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenTheLimitIsReached() {
        for (int i = 0; i < 5; i++) {
            limiter.record("k");
        }
        assertThatThrownBy(() -> limiter.requireBelow("k", 5, WINDOW)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void reportsHowLongToWait() {
        for (int i = 0; i < 5; i++) {
            limiter.record("k");
        }
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.requireBelow("k", 5, WINDOW))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e ->
                        // 10 minutos restantes (15 - 5), mais 1 s de folga
                        assertThat(e.getRetryAfterSeconds()).isBetween(600L, 601L));
    }

    @Test
    void unblocksAfterTheWindowPasses() {
        for (int i = 0; i < 5; i++) {
            limiter.record("k");
        }
        clock.advance(WINDOW.plusSeconds(1));

        assertThatCode(() -> limiter.requireBelow("k", 5, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void slidingWindowOnlyCountsRecentEvents() {
        limiter.record("k"); // t = 0
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 4; i++) {
            limiter.record("k"); // t = 10 min
        }
        // 5 eventos dentro de 15 min: bloqueia...
        assertThatThrownBy(() -> limiter.requireBelow("k", 5, WINDOW)).isInstanceOf(TooManyRequestsException.class);

        // ...mas 6 min depois o primeiro (t = 0) saiu da janela e sobram 4
        clock.advance(Duration.ofMinutes(6));
        assertThatCode(() -> limiter.requireBelow("k", 5, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.record("a");
        }
        assertThatCode(() -> limiter.requireBelow("b", 5, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void clearForgetsTheEvents() {
        for (int i = 0; i < 5; i++) {
            limiter.record("k");
        }
        limiter.clear("k");
        assertThatCode(() -> limiter.requireBelow("k", 5, WINDOW)).doesNotThrowAnyException();
    }
}
