package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.ActiveUsers;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.auth.infrastructure.SessionStore;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionAuthenticator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Sessões opacas (AD-12, história 2.1): o token são 32 bytes aleatórios em base64url, e {@code
 * sessions} guarda só o sha256 dele ({@link OpaqueTokens}). A sessão do {@code STUDENT} dura 30
 * dias e desliza: quando passou mais de 1 dia desde a última renovação, o vencimento volta a ser
 * agora + 30 dias.
 *
 * <p>É a implementação da porta {@link SessionAuthenticator} da {@code platform}: a cada requisição
 * com token, confere a sessão, o vencimento e o {@code status} da conta em {@code account}. O token
 * nunca vai para o log.
 */
@Service
public class Sessions implements SessionAuthenticator {

    /** A duração da sessão e do cookie do Aluno. */
    public static final Duration STUDENT_TTL = Duration.ofDays(30);

    /** De quanto em quanto tempo a sessão desliza. */
    static final Duration RENEW_EVERY = Duration.ofDays(1);

    private final SessionStore store;

    private final ActiveUsers users;

    private final Clock clock;

    Sessions(SessionStore store, ActiveUsers users, Clock clock) {
        this.store = store;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Cria a sessão da conta.
     *
     * @return o token, que só o cliente guarda, e o vencimento
     */
    public NewSession create(String userId, Role role) {
        String token = OpaqueTokens.generate();
        Instant now = now();
        Instant expiresAt = now.plus(STUDENT_TTL);
        this.store.insert(new Session(OpaqueTokens.hash(token), userId, role, now, expiresAt));
        return new NewSession(token, expiresAt);
    }

    @Override
    public Optional<AuthenticatedSession> authenticate(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String tokenHash = OpaqueTokens.hash(token);
        Optional<Session> found = this.store.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        Instant now = now();
        if (!session.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Optional<ActiveUsers.ActiveUser> user = this.users.find(session.userId());
        if (user.isEmpty()) {
            return Optional.empty();
        }
        Instant renewedUntil = null;
        // A última renovação foi em expiresAt - 30 dias.
        if (user.get().role() == Role.STUDENT
                && session.expiresAt().minus(STUDENT_TTL).plus(RENEW_EVERY).isBefore(now)) {
            renewedUntil = now.plus(STUDENT_TTL);
            this.store.renew(tokenHash, renewedUntil);
        }
        return Optional.of(
                new AuthenticatedSession(user.get().userId(), user.get().role(), renewedUntil));
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Uma sessão recém-criada.
     *
     * @param token o token, que nunca vai para o log
     */
    public record NewSession(String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "NewSession[expiresAt=" + this.expiresAt + "]";
        }
    }
}
