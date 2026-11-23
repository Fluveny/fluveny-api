package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O limite por {@code userId} das escritas do Aluno (história 2.4): um só contador para Tentativas,
 * "Ver resposta", conclusões e eventos, no molde do limite por aparelho do Visitante. Em memória,
 * por instância, em janela fixa de um minuto.
 */
@Component
class StudentWriteLimit {

    private final RateLimiter limiter;

    StudentWriteLimit(
            Clock clock, @Value("${fluveny.student.rate-limit.per-minute:60}") int perMinute) {
        this.limiter = new RateLimiter(perMinute, Duration.ofMinutes(1), clock);
    }

    /**
     * @throws com.fluveny.fluveny_backend.platform.web.RateLimitedException {@code 429
     *     TOO_MANY_REQUESTS} acima do limite
     */
    void acquire(String userId) {
        this.limiter.acquire(userId);
    }
}
