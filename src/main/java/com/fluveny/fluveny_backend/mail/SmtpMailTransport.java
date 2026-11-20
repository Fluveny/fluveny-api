package com.fluveny.fluveny_backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP genérico (história 2.2): o {@link JavaMailSender} que o Boot cria com {@code
 * spring.mail.host} ({@code SPRING_MAIL_HOST}, {@code SPRING_MAIL_PORT}, {@code
 * SPRING_MAIL_USERNAME}, {@code SPRING_MAIL_PASSWORD}) e o remetente {@code FLUVENY_MAIL_FROM}. O
 * provedor só muda essas variáveis. Sem o host ou sem o remetente, nada sai: os e-mails ficam
 * {@code PENDING} e a partida avisa no log.
 */
@Component
class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final JavaMailSender sender;

    private final String from;

    SmtpMailTransport(
            ObjectProvider<JavaMailSender> sender, @Value("${fluveny.mail.from:}") String from) {
        this.sender = sender.getIfAvailable();
        this.from = from.strip();
    }

    @Override
    public boolean configured() {
        return this.sender != null && !this.from.isEmpty();
    }

    @Override
    public void send(String to, String subject, String text) {
        if (!configured()) {
            throw new IllegalStateException("SMTP não configurado");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        this.sender.send(message);
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfNotConfigured() {
        if (this.sender == null) {
            log.warn(
                    "SMTP desligado (spring.mail.host vazio): os e-mails ficam PENDING em"
                            + " mail_outbox até SPRING_MAIL_HOST entrar.");
        } else if (this.from.isEmpty()) {
            log.warn(
                    "SMTP sem remetente (FLUVENY_MAIL_FROM vazio): os e-mails ficam PENDING em"
                            + " mail_outbox.");
        }
    }
}
