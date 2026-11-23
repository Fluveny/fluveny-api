package com.fluveny.fluveny_backend.account.infrastructure;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um documento de {@code users} (dono {@code account}, AD-13, história 2.1). O {@code passwordHash}
 * nunca sai de {@code account}; e-mail, nome e data de nascimento são dados pessoais e nunca vão
 * para o log nem para {@code events}.
 *
 * @param id o {@code _id} em hex; nulo antes de gravar
 * @param email normalizado (trim e minúsculas), com índice único
 * @param metricsId o identificador de {@code events}, com índice único; não é o {@code id}
 * @param name opcional
 * @param pronounSet opcional; nulo vale como they/them
 * @param emailVerifiedAt nulo até a confirmação (2.2)
 * @param pendingEmail nulo até a troca de e-mail (2.9)
 * @param deletionRequestedAt nulo até o pedido de exclusão (2.10)
 * @param country opcional: o {@code name} em inglês de um país de {@code countries.json}, informado
 *     no cadastro (história 2.6); dado pessoal
 */
public record User(
        String id,
        String email,
        String passwordHash,
        LocalDate birthDate,
        Role role,
        AccountStatus status,
        UUID metricsId,
        String name,
        PronounSet pronounSet,
        String termsVersion,
        String privacyVersion,
        Instant termsAcceptedAt,
        Instant createdAt,
        Instant emailVerifiedAt,
        String pendingEmail,
        Instant deletionRequestedAt,
        String country) {

    /** Uma conta sem país (as de antes da história 2.6 e as do carregador local). */
    public User(
            String id,
            String email,
            String passwordHash,
            LocalDate birthDate,
            Role role,
            AccountStatus status,
            UUID metricsId,
            String name,
            PronounSet pronounSet,
            String termsVersion,
            String privacyVersion,
            Instant termsAcceptedAt,
            Instant createdAt,
            Instant emailVerifiedAt,
            String pendingEmail,
            Instant deletionRequestedAt) {
        this(
                id,
                email,
                passwordHash,
                birthDate,
                role,
                status,
                metricsId,
                name,
                pronounSet,
                termsVersion,
                privacyVersion,
                termsAcceptedAt,
                createdAt,
                emailVerifiedAt,
                pendingEmail,
                deletionRequestedAt,
                null);
    }

    /** Sem e-mail, nome, país, data nem hash. */
    @Override
    public String toString() {
        return "User[id=" + this.id + ", role=" + this.role + ", status=" + this.status + "]";
    }
}
