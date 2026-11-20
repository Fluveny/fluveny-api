package com.fluveny.fluveny_backend.mail;

/** O transporte do despacho (história 2.2): SMTP em produção, falso nos testes. */
interface MailTransport {

    /** Se há para onde enviar; sem isso, o despacho não pega nada e tudo fica {@code PENDING}. */
    boolean configured();

    /**
     * Envia um e-mail em texto puro.
     *
     * @throws RuntimeException se o envio falhou (o despacho tenta de novo depois)
     */
    void send(String to, String subject, String text);
}
