package com.fluveny.fluveny_backend.mail;

import com.fluveny.fluveny_backend.mail.infrastructure.MailStatus;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * O {@link MailSender} do outbox (AD-15, história 2.2): grava {@code PENDING} e volta. O {@code
 * purgeAt} já nasce em 30 dias: sem SMTP, o e-mail (com endereço e link) não fica para sempre.
 */
@Service
class OutboxMailSender implements MailSender {

    private final OutboxStore store;

    private final Clock clock;

    OutboxMailSender(OutboxStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void enqueue(String to, String subject, String text, MailPriority priority) {
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
        this.store.insert(
                new OutboxMail(
                        null,
                        requireText(to, "to"),
                        requireText(subject, "subject"),
                        requireText(text, "text"),
                        Objects.requireNonNull(priority, "priority"),
                        MailStatus.PENDING,
                        0,
                        now,
                        now,
                        null,
                        now.plus(MailDispatcher.RETENTION)));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " vazio");
        }
        return value;
    }
}
