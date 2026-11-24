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
        insert(to, subject, text, priority, false);
    }

    @Override
    public void enqueuePurgedOnSend(String to, String subject, String text, MailPriority priority) {
        insert(to, subject, text, priority, true);
    }

    @Override
    public void discard(String to, String subject) {
        this.store.deleteAllTo(to, subject);
    }

    private void insert(
            String to, String subject, String text, MailPriority priority, boolean purgeOnSend) {
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
                        now.plus(
                                purgeOnSend
                                        ? MailDispatcher.PURGED_ON_SEND_MAX_RETENTION
                                        : MailDispatcher.RETENTION),
                        purgeOnSend));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " vazio");
        }
        return value;
    }
}
