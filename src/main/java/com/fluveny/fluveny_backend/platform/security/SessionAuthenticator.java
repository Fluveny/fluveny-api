package com.fluveny.fluveny_backend.platform.security;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Porta da sessão do usuário (AD-12, história 2.1): a {@code platform} lê o token do cookie {@link
 * SessionCookie#NAME} ou do {@code Authorization: Bearer} e pergunta aqui de quem é. O módulo
 * {@code auth} implementa, conferindo a sessão em {@code sessions} e o {@code status} da conta em
 * {@code account}. Sem implementação, nenhuma sessão é aceita.
 */
public interface SessionAuthenticator {

    /**
     * A sessão do token, se ela existe, não venceu e a conta está ativa. Uma sessão deslizante pode
     * ser renovada aqui ({@link AuthenticatedSession#renewedUntil()}).
     *
     * @param token o token como o cliente enviou (nunca vai para o log)
     * @return vazio se o token não é de ninguém, venceu ou a conta não está ativa
     */
    Optional<AuthenticatedSession> authenticate(String token);

    /**
     * Uma sessão reconhecida.
     *
     * @param userId o id do usuário, a única fonte do {@code userId} nas rotas
     * @param role o papel do usuário
     * @param renewedUntil o novo vencimento, se a sessão foi renovada agora; senão {@code null}
     */
    record AuthenticatedSession(String userId, Role role, Instant renewedUntil) {

        public AuthenticatedSession {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(role, "role");
        }
    }
}
