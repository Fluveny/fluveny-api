package com.fluveny.fluveny_backend.account.infrastructure;

import com.fluveny.fluveny_backend.account.PronounSet;
import java.util.Optional;

/** Coleção {@code users} (dona: {@code account}, AD-13). Só {@code account} chama. */
public interface UserStore {

    /**
     * Grava a conta nova.
     *
     * @return o id gravado; vazio se o e-mail já tem conta (o índice único decide, inclusive em
     *     dois cadastros ao mesmo tempo)
     */
    Optional<String> insert(User user);

    /** A conta do id; vazio se não existe ou o id não é válido. */
    Optional<User> findById(String id);

    /**
     * Grava os pronomes.
     *
     * @return {@code false} se a conta não existe
     */
    boolean updatePronounSet(String id, PronounSet pronounSet);

    /**
     * Para o carregador local: cria a conta do e-mail ou atualiza senha, nome, pronomes, papel e
     * status dela, sem trocar o {@code metricsId} nem o {@code createdAt}.
     */
    void upsertByEmail(User user);
}
