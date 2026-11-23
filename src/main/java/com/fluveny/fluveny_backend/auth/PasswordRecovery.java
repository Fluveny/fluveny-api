package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.PasswordReset;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.RateLimitedException;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recuperação de senha, o lado da sessão (história 2.8, FR-2, AD-12): os limites do pedido, o
 * executor que tira da requisição tudo o que depende da conta e o encerramento de todas as sessões
 * na redefinição. O token e o e-mail são de {@code account} ({@link PasswordReset}).
 *
 * <p>Limites, em memória e por instância (como o reenvio da 2.2), além dos 300/min por IP do {@code
 * VisitorRateLimitFilter}:
 *
 * <ul>
 *   <li>por IP, {@value #REQUESTS_PER_IP_PER_HOUR} pedidos por hora (a rede compartilhada de uma
 *       turma, como na 2.3): acima disso, {@code 429 TOO_MANY_REQUESTS} com {@code Retry-After},
 *       igual para qualquer e-mail;
 *   <li>em silêncio (a resposta segue {@code 202} e nada é enfileirado, porque um {@code 429} só
 *       para contas existentes revelaria a conta), com chaves sha256, nunca o e-mail em claro: por
 *       e-mail normalizado, {@value #REQUESTS_PER_EMAIL_PER_MINUTE} por minuto; por e-mail e
 *       origem, {@value #REQUESTS_PER_EMAIL_ORIGIN_PER_DAY} por dia; por e-mail, {@value
 *       #REQUESTS_PER_EMAIL_PER_DAY} por dia. Assim, um terceiro que saiba o e-mail não esgota
 *       sozinho a cota do titular.
 * </ul>
 *
 * <p>Nem o e-mail, nem o token, nem a senha, nem o IP vão para o log ou o Sentry.
 */
@Service
public class PasswordRecovery {

    static final int REQUESTS_PER_IP_PER_HOUR = 20;

    static final int REQUESTS_PER_EMAIL_PER_MINUTE = 1;

    static final int REQUESTS_PER_EMAIL_ORIGIN_PER_DAY = 5;

    static final int REQUESTS_PER_EMAIL_PER_DAY = 20;

    private static final Logger log = LoggerFactory.getLogger(PasswordRecovery.class);

    private final PasswordReset reset;

    private final Sessions sessions;

    private final PasswordRecoveryExecutor executor;

    private final RateLimiter perIp;

    private final RateLimiter perEmailMinute;

    private final RateLimiter perEmailOriginDay;

    private final RateLimiter perEmailDay;

    PasswordRecovery(
            PasswordReset reset,
            Sessions sessions,
            PasswordRecoveryExecutor executor,
            Clock clock) {
        this.reset = reset;
        this.sessions = sessions;
        this.executor = executor;
        this.perIp = new RateLimiter(REQUESTS_PER_IP_PER_HOUR, Duration.ofHours(1), clock);
        this.perEmailMinute =
                new RateLimiter(REQUESTS_PER_EMAIL_PER_MINUTE, Duration.ofMinutes(1), clock);
        this.perEmailOriginDay =
                new RateLimiter(REQUESTS_PER_EMAIL_ORIGIN_PER_DAY, Duration.ofDays(1), clock);
        this.perEmailDay = new RateLimiter(REQUESTS_PER_EMAIL_PER_DAY, Duration.ofDays(1), clock);
    }

    /**
     * Pede o link. Só confere os limites e enfileira a tarefa: a busca da conta, o token e o outbox
     * rodam depois, no {@link PasswordRecoveryExecutor}. Volta igual para e-mail existente ou não.
     *
     * @param email como digitado
     * @param ip o IP da origem ({@code getRemoteAddr()}, já resolvido pelo {@code
     *     ProxySecretFilter})
     * @throws RateLimitedException {@code 429 TOO_MANY_REQUESTS} acima de {@value
     *     #REQUESTS_PER_IP_PER_HOUR} pedidos por hora do IP
     */
    public void request(String email, String ip) {
        String origin = LoginThrottle.origin(ip);
        this.perIp.acquire(key("ip", origin));
        String normalized = RegisterStudent.normalizeEmail(email);
        String emailKey = key("email", normalized);
        if (this.perEmailMinute.tryAcquire(emailKey) > 0
                || this.perEmailOriginDay.tryAcquire(key("email_ip", normalized + "\n" + origin))
                        > 0
                || this.perEmailDay.tryAcquire(emailKey) > 0) {
            return;
        }
        switch (this.executor.submit(() -> issue(normalized))) {
            case ACCEPTED -> {
                // Segue na fila.
            }
            case QUEUE_FULL -> log.warn("Fila da recuperação de senha cheia: pedido descartado");
            case SHUT_DOWN ->
                    log.warn("Executor da recuperação de senha encerrado: pedido descartado");
        }
    }

    /**
     * Redefine a senha pelo token do link e encerra todas as sessões da conta. Não abre sessão: o
     * App leva a Entrar.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} (senha fora da regra, token intacto) ou
     *     {@code 410 PASSWORD_RESET_LINK_INVALID}
     */
    public void reset(String token, String password) {
        String userId = this.reset.reset(token, password);
        // Sem transação: a senha nova já vale; se isto falhar, as sessões antigas ficam até o TTL.
        this.sessions.revokeAll(userId);
    }

    private void issue(String email) {
        try {
            this.reset.request(email);
        } catch (RuntimeException ex) {
            // Sem a mensagem original: ela pode trazer o e-mail.
            log.error("Falha ao emitir a redefinição de senha ({})", ex.getClass().getName());
            IllegalStateException sanitized =
                    new IllegalStateException(
                            "Falha ao emitir a redefinição de senha ("
                                    + ex.getClass().getName()
                                    + ")");
            sanitized.setStackTrace(ex.getStackTrace());
            Sentry.captureException(sanitized);
        }
    }

    private static String key(String kind, String value) {
        return OpaqueTokens.hash("fluveny-password-recovery\n" + kind + "\n" + value);
    }
}
