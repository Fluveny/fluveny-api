package com.fluveny.fluveny_backend.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;

/** Coleção {@code sessions} (dona: {@code auth}, AD-12). Só {@code auth} chama. */
public interface SessionStore {

    void insert(Session session);

    /** A sessão do hash, vencida ou não; vazio se não existe. */
    Optional<Session> findByTokenHash(String tokenHash);

    /** Troca o vencimento da sessão (a renovação deslizante). */
    void renew(String tokenHash, Instant expiresAt);

    /** Apaga a sessão do hash, se existe (o logout, história 2.3). */
    void deleteByTokenHash(String tokenHash);

    /** Apaga todas as sessões da conta (a redefinição de senha, história 2.8). */
    void deleteByUser(String userId);
}
