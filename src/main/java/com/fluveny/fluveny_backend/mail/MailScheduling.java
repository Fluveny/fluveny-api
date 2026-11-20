package com.fluveny.fluveny_backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * O despacho agendado do outbox (história 2.2): a cada 30 s, enquanto a instância está acordada.
 * Desliga com {@code fluveny.mail.dispatch.enabled=false} (os testes chamam o {@link
 * MailDispatcher} direto), sem afetar outro {@code @Scheduled}: o {@code @EnableScheduling} fica na
 * {@code platform}. Um erro numa rodada (o Mongo fora do ar, por exemplo) vai para o log e a
 * próxima rodada tenta de novo.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "fluveny.mail.dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true)
class MailScheduling {

    private static final Logger log = LoggerFactory.getLogger(MailScheduling.class);

    private final MailDispatcher dispatcher;

    MailScheduling(MailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void dispatch() {
        try {
            this.dispatcher.dispatch();
        } catch (RuntimeException ex) {
            log.error("Falha no despacho do outbox ({}).", ex.getClass().getName());
        }
    }
}
