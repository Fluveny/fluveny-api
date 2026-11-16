package com.fluveny.fluveny_backend.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Janela fixa em memória (Decisão 1 da história 1.12). */
class RateLimiterTest {

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-16T12:00:00Z");

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private final RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), this.clock);

    @Test
    void allowsTheLimitPerWindowAndThenRefusesWithRetryAfter() {
        assertThat(this.limiter.tryAcquire("a")).isZero();
        assertThat(this.limiter.tryAcquire("a")).isZero();
        assertThat(this.limiter.tryAcquire("a")).isZero();
        this.clock.advance(Duration.ofSeconds(20));
        assertThat(this.limiter.tryAcquire("a")).isEqualTo(40);
        // Outra chave tem a própria contagem.
        assertThat(this.limiter.tryAcquire("b")).isZero();
    }

    @Test
    void theCountResetsInTheNextWindow() {
        for (int i = 0; i < 3; i++) {
            this.limiter.tryAcquire("a");
        }
        assertThat(this.limiter.tryAcquire("a")).isPositive();
        this.clock.advance(Duration.ofMinutes(1));
        assertThat(this.limiter.tryAcquire("a")).isZero();
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        for (int i = 0; i < 3; i++) {
            this.limiter.tryAcquire("a");
        }
        this.clock.advance(Duration.ofMillis(59_999));
        assertThat(this.limiter.tryAcquire("a")).isEqualTo(1);
    }

    @Test
    void acquireThrows429WithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            this.limiter.acquire("a");
        }
        assertThatThrownBy(() -> this.limiter.acquire("a"))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                            assertThat(ex.getStatus().value()).isEqualTo(429);
                            assertThat(ex.getRetryAfterSeconds()).isEqualTo(60);
                        });
    }

    @Test
    void rejectsANonPositiveLimitOrWindow() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
