package com.fluveny.fluveny_backend.account.infrastructure;

import com.fluveny.fluveny_backend.account.ProfileChange;
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
     * Grava os campos presentes da troca de perfil (história 2.9), se a conta é um Aluno {@code
     * ACTIVE}; nome e país nulos saem do documento.
     *
     * @return {@code false} se a conta não existe ou não é um Aluno ativo
     */
    boolean updateProfile(String id, ProfileChange change);

    /**
     * Troca o hash da senha (a troca com a senha atual, história 2.9), só se a conta ainda está
     * {@code ACTIVE} e com o hash {@code currentHash}: duas trocas ao mesmo tempo não se sobrepõem.
     *
     * @return {@code true} se o hash foi trocado
     */
    boolean replacePasswordHash(String id, String currentHash, String newHash);

    /**
     * Grava o e-mail novo pedido na troca (história 2.9), se a conta está {@code ACTIVE}.
     *
     * @return {@code false} se a conta não existe ou não está {@code ACTIVE}
     */
    boolean setPendingEmail(String id, String pendingEmail);

    /**
     * Numa só atualização condicional, troca o {@code email} pelo {@code pendingEmail}, se ele
     * ainda é {@code pendingEmail} e a conta está {@code ACTIVE}; zera o {@code pendingEmail} e
     * grava o {@code emailVerifiedAt}. O índice único do e-mail decide a corrida.
     */
    PromotionResult promotePendingEmail(String id, String pendingEmail, Instant verifiedAt);

    /** Zera o {@code pendingEmail}, se ele ainda é {@code pendingEmail}. */
    void clearPendingEmail(String id, String pendingEmail);

    /** O resultado de {@link #promotePendingEmail}. */
    enum PromotionResult {
        /** O e-mail novo é o da conta, já confirmado. */
        PROMOTED,
        /** A conta não tem mais esse e-mail pendente (ou não está ativa): nada mudou. */
        NOT_PENDING,
        /** Outra conta já tem o e-mail novo: nada mudou. */
        TAKEN
    }

    /**
     * Para o carregador local: cria a conta do e-mail ou atualiza senha, nome, pronomes, papel,
     * status, país e {@code emailVerifiedAt} dela, zera o {@code pendingEmail}, sem trocar o {@code
     * metricsId} nem o {@code createdAt}.
     */
    void upsertByEmail(User user);
}
