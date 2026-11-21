package com.fluveny.fluveny_backend.auth.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Coleção {@code login_throttle} (dona: {@code auth}, história 2.3). Só {@code auth} chama. */
public interface LoginThrottleStore {

    /** Os documentos das chaves que existem, vencidos ou não. */
    List<ThrottleEntry> find(Collection<String> keyHashes);

    /**
     * Conta uma falha da chave: soma 1 (ou começa de 1, se o documento venceu ou não existe) e
     * grava a hora e o vencimento.
     *
     * @param kind o tipo da chave ({@code ip}, {@code account_ip}, {@code account}), sem dado
     *     pessoal
     */
    void recordFailure(String keyHash, String kind, Instant now, Instant expiresAt);

    /** Zera as chaves (o sucesso). */
    void delete(Collection<String> keyHashes);
}
