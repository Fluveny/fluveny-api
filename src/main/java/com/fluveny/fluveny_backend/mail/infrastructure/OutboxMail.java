package com.fluveny.fluveny_backend.mail.infrastructure;

import com.fluveny.fluveny_backend.mail.MailPriority;
import java.time.Instant;

/**
 * Um documento de {@code mail_outbox} (dono {@code mail}, AD-15, história 2.2). O {@code to} e o
 * {@code text} são dados pessoais (o texto pode levar um link com token) e nunca vão para o log;
 * por isso o documento some 30 dias depois de enfileirado ou de {@code SENT} ou {@code FAILED}
 * ({@code purgeAt}, com TTL).
 *
 * @param id o {@code _id} em hex; nulo antes de gravar
 * @param attempts as tentativas de envio já feitas
 * @param nextAttemptAt quando o despacho pode pegar o e-mail
 * @param sentAt nulo até {@code SENT}
 * @param purgeAt quando o TTL apaga: 30 dias depois do enqueue, regravado no {@code SENT} e no
 *     {@code FAILED}
 */
public record OutboxMail(
        String id,
        String to,
        String subject,
        String text,
        MailPriority priority,
        MailStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant sentAt,
        Instant purgeAt) {

    /** Sem endereço, assunto nem texto. */
    @Override
    public String toString() {
        return "OutboxMail[id="
                + this.id
                + ", priority="
                + this.priority
                + ", status="
                + this.status
                + ", attempts="
                + this.attempts
                + "]";
    }
}
