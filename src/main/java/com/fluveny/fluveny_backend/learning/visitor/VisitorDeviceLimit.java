package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O limite por {@code deviceId} das rotas do Visitante (Decisão 1 da 1.12): um só contador por
 * aparelho para Tentativas, "Ver resposta", conclusões e eventos (história 1.20). O limite por IP
 * fica no filtro da {@code platform}.
 */
@Component
class VisitorDeviceLimit {

    private final RateLimiter limiter;

    VisitorDeviceLimit(
            Clock clock,
            @Value("${fluveny.visitor.rate-limit.device-per-minute:30}") int devicePerMinute) {
        this.limiter = new RateLimiter(devicePerMinute, Duration.ofMinutes(1), clock);
    }

    /**
     * @throws com.fluveny.fluveny_backend.platform.web.RateLimitedException {@code 429
     *     TOO_MANY_REQUESTS} acima do limite
     */
    void acquire(UUID deviceId) {
        this.limiter.acquire(deviceId.toString());
    }
}
