package com.fluveny.fluveny_backend.mail.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Coleção {@code mail_outbox} (dona: {@code mail}, AD-15). Só {@code mail} chama. */
public interface OutboxStore {

    /** Grava o e-mail novo, {@code PENDING}; devolve o id. */
    String insert(OutboxMail mail);

    /**
     * Pega, de forma atômica, o próximo {@code PENDING} vencido ({@code nextAttemptAt <= now}), por
     * prioridade ({@code HIGH} antes) e {@code createdAt}, e adia o {@code nextAttemptAt} dele para
     * {@code leaseUntil}: outro despacho, nesta ou em outra instância, não o pega de novo. Se a
     * instância cair no meio do envio, ele volta à fila quando o prazo passa.
     *
     * @return o e-mail como estava antes da marca; vazio se não há nenhum
     */
    Optional<OutboxMail> claimNext(Instant now, Instant leaseUntil);

    /** {@code SENT}, com {@code sentAt}, as tentativas e o {@code purgeAt}. */
    void markSent(String id, int attempts, Instant sentAt, Instant purgeAt);

    /** Continua {@code PENDING}, com as tentativas e a próxima vez. */
    void markRetry(String id, int attempts, Instant nextAttemptAt);

    /** {@code FAILED}, com as tentativas e o {@code purgeAt}. */
    void markFailed(String id, int attempts, Instant purgeAt);

    /** Quantos {@code SENT} têm {@code sentAt >= since}: a conta da cota diária. */
    long countSentSince(Instant since);

    /** Os últimos e-mails, do mais novo ao mais antigo (só o perfil {@code local} usa). */
    List<OutboxMail> latest(int limit);

    /**
     * Apaga todo e-mail para um dos endereços, em qualquer {@code status} (a exclusão da conta,
     * história 2.10).
     */
    void deleteAllTo(Collection<String> addresses);
}
