package com.fluveny.fluveny_backend.mail;

import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Uma rodada do despacho do outbox (AD-15, história 2.2). Com o transporte configurado, conta os
 * {@code SENT} do dia em {@code America/Sao_Paulo} e, até o que cabe na cota, marca de forma
 * atômica o próximo {@code PENDING} vencido ({@code HIGH} antes, depois o mais antigo), envia e só
 * então marca o seguinte. Cota esgotada: nada é marcado e nada gasta tentativa; o resto espera a
 * virada do dia.
 *
 * <p>Falha de envio: a tentativa conta, e a próxima vem com espera exponencial de 1 min a 1 h; na
 * {@value #MAX_ATTEMPTS}ª, o e-mail fica {@code FAILED}. O log e o Sentry levam só o id e a classe
 * da exceção: a mensagem do SMTP pode trazer o endereço. Autenticação recusada pelo SMTP (senha
 * errada) é erro de configuração: não conta tentativa, o e-mail espera 1 h e a rodada para.
 */
@Component
class MailDispatcher {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    static final int MAX_ATTEMPTS = 8;

    static final Duration FIRST_BACKOFF = Duration.ofMinutes(1);

    static final Duration MAX_BACKOFF = Duration.ofHours(1);

    /** Por quanto tempo o e-mail marcado fica fora da fila se a instância cair no envio. */
    static final Duration LEASE = Duration.ofMinutes(10);

    /** Quanto tempo o e-mail {@code SENT} ou {@code FAILED} fica no outbox. */
    static final Duration RETENTION = Duration.ofDays(30);

    /** O máximo de e-mails por rodada. */
    static final int BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final OutboxStore store;

    private final MailTransport transport;

    private final Clock clock;

    private final int dailyQuota;

    MailDispatcher(
            OutboxStore store,
            MailTransport transport,
            Clock clock,
            @Value("${fluveny.mail.daily-quota:300}") int dailyQuota) {
        if (dailyQuota < 0) {
            throw new IllegalStateException("fluveny.mail.daily-quota não pode ser negativa.");
        }
        this.store = store;
        this.transport = transport;
        this.clock = clock;
        this.dailyQuota = dailyQuota;
    }

    /**
     * Roda uma vez.
     *
     * @return quantos e-mails saíram
     */
    synchronized int dispatch() {
        if (!this.transport.configured()) {
            return 0;
        }
        Instant now = now();
        Instant startOfDay = LocalDate.ofInstant(now, ZONE).atStartOfDay(ZONE).toInstant();
        long remaining = this.dailyQuota - this.store.countSentSince(startOfDay);
        if (remaining <= 0) {
            return 0;
        }
        // Um por vez: marca, entrega, marca o próximo. Assim o lease de cada e-mail só corre
        // durante o envio dele, mesmo com o SMTP lento.
        int sent = 0;
        for (int claimed = 0; claimed < Math.min(remaining, BATCH); claimed++) {
            Instant claimAt = now();
            Optional<OutboxMail> mail = this.store.claimNext(claimAt, claimAt.plus(LEASE));
            if (mail.isEmpty()) {
                break;
            }
            Delivery delivery = deliver(mail.get());
            if (delivery == Delivery.SENT) {
                sent++;
            } else if (delivery == Delivery.STOP) {
                break;
            }
        }
        return sent;
    }

    private enum Delivery {
        SENT,
        RETRY,
        /** Erro de configuração do SMTP: a rodada para, sem gastar tentativa. */
        STOP
    }

    private Delivery deliver(OutboxMail mail) {
        int attempts = mail.attempts() + 1;
        try {
            this.transport.send(mail.to(), mail.subject(), mail.text());
        } catch (MailAuthenticationException ex) {
            // Credencial errada não é culpa do e-mail: não conta tentativa, volta à fila em 1 h e
            // a rodada para (os outros falhariam igual). Corrigida a variável, tudo sai.
            this.store.markRetry(mail.id(), mail.attempts(), now().plus(MAX_BACKOFF));
            log.error(
                    "SMTP recusou a autenticação no e-mail {} ({}); despacho pausado por 1 h.",
                    mail.id(),
                    ex.getClass().getName());
            return Delivery.STOP;
        } catch (RuntimeException ex) {
            Instant now = now();
            if (attempts >= MAX_ATTEMPTS) {
                this.store.markFailed(mail.id(), attempts, now.plus(RETENTION));
                log.error(
                        "E-mail {} FAILED após {} tentativas ({}).",
                        mail.id(),
                        attempts,
                        ex.getClass().getName());
                Sentry.captureException(sanitized(mail, attempts, ex));
            } else {
                this.store.markRetry(mail.id(), attempts, now.plus(backoff(attempts)));
                log.warn(
                        "Falha ao enviar o e-mail {} (tentativa {}, {}).",
                        mail.id(),
                        attempts,
                        ex.getClass().getName());
            }
            return Delivery.RETRY;
        }
        Instant sentAt = now();
        this.store.markSent(mail.id(), attempts, sentAt, sentAt.plus(RETENTION));
        return Delivery.SENT;
    }

    /** A espera depois da tentativa {@code attempts}: 1, 2, 4… minutos, até 1 h. */
    static Duration backoff(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 30);
        Duration delay = FIRST_BACKOFF.multipliedBy(1L << shift);
        return (delay.compareTo(MAX_BACKOFF) > 0) ? MAX_BACKOFF : delay;
    }

    /** A exceção para o Sentry, sem a mensagem original (pode ter o endereço). */
    private static RuntimeException sanitized(OutboxMail mail, int attempts, RuntimeException ex) {
        IllegalStateException copy =
                new IllegalStateException(
                        "E-mail "
                                + mail.id()
                                + " FAILED após "
                                + attempts
                                + " tentativas ("
                                + ex.getClass().getName()
                                + ")");
        copy.setStackTrace(ex.getStackTrace());
        return copy;
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
