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
     * @param accountKeyHash a chave {@code account} da conta, gravada nos documentos {@code
     *     account} e {@code account_ip} para a exclusão da conta achá-los (história 2.10); nulo na
     *     chave {@code ip}, que não identifica a conta
     */
    void recordFailure(
            String keyHash, String kind, String accountKeyHash, Instant now, Instant expiresAt);

    /** Zera as chaves (o sucesso). */
    void delete(Collection<String> keyHashes);

    /**
     * Apaga os documentos {@code account} e {@code account_ip} das contas (a exclusão, história
     * 2.10): os de chave igual a uma delas e os que a guardam em {@code accountKeyHash}.
     */
    void deleteByAccount(Collection<String> accountKeyHashes);
}
