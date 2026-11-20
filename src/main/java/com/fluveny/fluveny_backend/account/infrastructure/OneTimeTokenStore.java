package com.fluveny.fluveny_backend.account.infrastructure;

import java.time.Instant;
import java.util.Optional;

/** Coleção {@code one_time_tokens} (dona: {@code account}, AD-13). Só {@code account} chama. */
public interface OneTimeTokenStore {

    void insert(OneTimeToken token);

    /**
     * Consome o token: acha e apaga na mesma operação, pelo hash, pelo propósito e com {@code
     * expiresAt > now}. Duas chamadas ao mesmo tempo: só uma recebe o token.
     *
     * @return o token apagado; vazio se não existe, é de outro propósito ou venceu
     */
    Optional<OneTimeToken> consume(String tokenHash, TokenPurpose purpose, Instant now);

    /** Apaga os tokens da conta com o propósito (o reenvio invalida o link anterior). */
    void deleteByUser(String userId, TokenPurpose purpose);
}
