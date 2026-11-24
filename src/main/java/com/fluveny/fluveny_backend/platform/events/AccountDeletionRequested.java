package com.fluveny.fluveny_backend.platform.events;

import java.util.Objects;
import java.util.UUID;

/**
 * O Aluno pediu para excluir a conta (história 2.10, FR-6, AD-3, AD-13): publicado por {@code
 * account} dentro da transação da exclusão, depois de marcar o {@code deletionRequestedAt} e antes
 * de apagar o documento de {@code users}. Cada módulo dono de dado pessoal trata num
 * {@code @EventListener} síncrono e apaga o que é seu na mesma transação; uma falha desfaz tudo.
 *
 * <p>Só vive em memória: nunca vai para {@code events} nem para o log. Os tratadores leem daqui o
 * e-mail e o {@code metricsId}, porque o documento de {@code users} some logo depois.
 *
 * @param userId o id da conta
 * @param metricsId o identificador de {@code events} da conta, que fica desvinculado
 * @param email o e-mail da conta
 * @param pendingEmail o e-mail novo de uma troca pendente (história 2.9); nulo se não há ou se ele
 *     já é o e-mail de outra conta
 */
public record AccountDeletionRequested(
        String userId, UUID metricsId, String email, String pendingEmail) {

    /** A ordem dos tratadores dos módulos ({@code @Order}). */
    public static final int HANDLER_ORDER = 100;

    public AccountDeletionRequested {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(metricsId, "metricsId");
        Objects.requireNonNull(email, "email");
    }

    /** Só o tipo: nada de id, e-mail nem {@code metricsId}. */
    @Override
    public String toString() {
        return "AccountDeletionRequested[***]";
    }
}
