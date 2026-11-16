package com.fluveny.fluveny_backend.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de taxa em janela fixa e em memória, por instância (Decisão 1 da história 1.12): cada
 * chave pode fazer {@code limit} chamadas por janela de {@code window}; a contagem volta a zero no
 * início da janela seguinte. Sem dependência nova e sem estado compartilhado entre instâncias.
 *
 * <p>As chaves das janelas que já passaram são apagadas na primeira chamada de uma janela nova,
 * para o mapa não crescer sem fim.
 */
public final class RateLimiter {

    private final int limit;

    private final long windowMillis;

    private final Clock clock;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private volatile long sweptWindow = -1;

    /**
     * @param limit chamadas permitidas por janela, maior que zero
     * @param window duração da janela, maior que zero
     */
    public RateLimiter(int limit, Duration window, Clock clock) {
        if (limit <= 0 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("limit e window precisam ser maiores que zero.");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public RateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    /**
     * Conta uma chamada da chave.
     *
     * @return {@code 0} se a chamada cabe na janela; senão, os segundos (arredondados para cima, no
     *     mínimo 1) até a janela seguinte, para o {@code Retry-After}
     */
    public long tryAcquire(String key) {
        long now = this.clock.millis();
        long window = Math.floorDiv(now, this.windowMillis);
        sweep(window);
        Counter counter =
                this.counters.compute(
                        key,
                        (k, current) ->
                                (current == null || current.window != window)
                                        ? new Counter(window, 1)
                                        : new Counter(window, current.count + 1));
        if (counter.count <= this.limit) {
            return 0;
        }
        long remaining = (window + 1) * this.windowMillis - now;
        return Math.max(1, (remaining + 999) / 1000);
    }

    /**
     * Como {@link #tryAcquire}, mas lança.
     *
     * @throws RateLimitedException {@code 429 TOO_MANY_REQUESTS} com o {@code Retry-After}
     */
    public void acquire(String key) {
        long retryAfter = tryAcquire(key);
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
        }
    }

    private void sweep(long window) {
        if (this.sweptWindow == window) {
            return;
        }
        this.sweptWindow = window;
        this.counters.values().removeIf(counter -> counter.window < window);
    }

    private record Counter(long window, int count) {}
}
