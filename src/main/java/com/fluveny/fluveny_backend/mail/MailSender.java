package com.fluveny.fluveny_backend.mail;

import org.springframework.modulith.NamedInterface;

/**
 * A porta de envio de e-mail (AD-15, história 2.2): todo e-mail da API passa por aqui. O {@link
 * #enqueue} só grava em {@code mail_outbox} e volta na hora; o envio é do despacho agendado de
 * {@code mail}, nunca da requisição. O endereço e o texto nunca vão para o log.
 */
@NamedInterface("sender")
public interface MailSender {

    /**
     * Põe o e-mail na fila.
     *
     * @param to o destinatário
     * @param subject o assunto
     * @param text o corpo, em texto puro
     * @param priority {@link MailPriority#HIGH} sai antes de {@link MailPriority#NORMAL}
     */
    void enqueue(String to, String subject, String text, MailPriority priority);
}
