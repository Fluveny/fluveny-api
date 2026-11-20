package com.fluveny.fluveny_backend.mail.infrastructure;

/** O estado de um e-mail em {@code mail_outbox} (história 2.2). */
public enum MailStatus {
    /** Na fila, à espera do despacho ou de nova tentativa. */
    PENDING,
    /** Enviado ao servidor SMTP. */
    SENT,
    /** Desistiu depois da última tentativa. */
    FAILED
}
