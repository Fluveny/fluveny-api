package com.fluveny.fluveny_backend.account.infrastructure;

import com.fluveny.fluveny_backend.account.PronounSet;
import java.time.Instant;
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

    /** A conta do e-mail já normalizado (trim e minúsculas); vazio se não existe. */
    Optional<User> findByEmail(String email);

    /**
     * Grava os pronomes.
     *
     * @return {@code false} se a conta não existe
     */
    boolean updatePronounSet(String id, PronounSet pronounSet);

    /**
     * Grava o {@code emailVerifiedAt}, só se o e-mail da conta ainda é {@code email} e se ela ainda
     * não estava confirmada (a primeira confirmação fica; repetir não muda nada).
     *
     * @return {@code true} se a conta tem esse e-mail e agora está confirmada
     */
    boolean markEmailVerified(String id, String email, Instant verifiedAt);

    /**
     * Grava o hash da senha nova (a redefinição, história 2.8), só se a conta ainda está {@code
     * ACTIVE}, com o e-mail confirmado e com o e-mail {@code email}: a desativação ou a troca de
     * e-mail depois do pedido fecham o link.
     *
     * @return {@code true} se a conta cumpria as três condições e o hash foi gravado
     */
    boolean updatePasswordHash(String id, String email, String passwordHash);

    /**
     * Para o carregador local: cria a conta do e-mail ou atualiza senha, nome, pronomes, papel,
     * status e {@code emailVerifiedAt} dela, sem trocar o {@code metricsId} nem o {@code
     * createdAt}.
     */
    void upsertByEmail(User user);
}
