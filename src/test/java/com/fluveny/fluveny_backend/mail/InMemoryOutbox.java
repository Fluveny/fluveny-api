package com.fluveny.fluveny_backend.mail;

import com.fluveny.fluveny_backend.mail.infrastructure.MailStatus;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.bson.types.ObjectId;

/**
 * {@code mail_outbox} em memória, com as mesmas regras do {@code MongoOutboxStore} (a marca do
 * {@code claimNext} é atômica), para os testes sem banco (história 2.2).
 */
public class InMemoryOutbox implements OutboxStore {

    private final Map<String, OutboxMail> mails = new LinkedHashMap<>();

    /** Quando ligado, o {@code insert} lança, como um Mongo fora do ar. */
    public volatile boolean failInserts;

    public synchronized void clear() {
        this.mails.clear();
        this.failInserts = false;
    }

    /** Os e-mails, na ordem de gravação. */
    public synchronized List<OutboxMail> all() {
        return new ArrayList<>(this.mails.values());
    }

    public synchronized OutboxMail get(String id) {
        return this.mails.get(id);
    }

    @Override
    public synchronized String insert(OutboxMail mail) {
        if (this.failInserts) {
            throw new IllegalStateException("mail_outbox fora do ar");
        }
        String id = new ObjectId().toHexString();
        this.mails.put(
                id,
                with(
                        mail,
                        id,
                        mail.status(),
                        mail.attempts(),
                        mail.nextAttemptAt(),
                        mail.sentAt(),
                        mail.purgeAt()));
        return id;
    }

    @Override
    public synchronized Optional<OutboxMail> claimNext(Instant now, Instant leaseUntil) {
        Optional<OutboxMail> next =
                this.mails.values().stream()
                        .filter(mail -> mail.status() == MailStatus.PENDING)
                        .filter(mail -> !mail.nextAttemptAt().isAfter(now))
                        .min(
                                Comparator.comparing((OutboxMail mail) -> mail.priority().name())
                                        .thenComparing(OutboxMail::createdAt));
        next.ifPresent(mail ->
                update(
                        mail.id(),
                        m ->
                                with(
                                        m,
                                        m.id(),
                                        m.status(),
                                        m.attempts(),
                                        leaseUntil,
                                        m.sentAt(),
                                        m.purgeAt())));
        return next;
    }

    @Override
    public synchronized void markSent(String id, int attempts, Instant sentAt, Instant purgeAt) {
        update(id, m -> with(m, id, MailStatus.SENT, attempts, m.nextAttemptAt(), sentAt, purgeAt));
    }

    @Override
    public synchronized void markRetry(String id, int attempts, Instant nextAttemptAt) {
        update(id, m -> with(m, id, m.status(), attempts, nextAttemptAt, m.sentAt(), m.purgeAt()));
    }

    @Override
    public synchronized void markFailed(String id, int attempts, Instant purgeAt) {
        update(
                id,
                m ->
                        with(
                                m,
                                id,
                                MailStatus.FAILED,
                                attempts,
                                m.nextAttemptAt(),
                                m.sentAt(),
                                purgeAt));
    }

    @Override
    public synchronized long countSentSince(Instant since) {
        return this.mails.values().stream()
                .filter(mail -> mail.status() == MailStatus.SENT)
                .filter(mail -> !mail.sentAt().isBefore(since))
                .count();
    }

    @Override
    public synchronized List<OutboxMail> latest(int limit) {
        List<OutboxMail> all = new ArrayList<>(this.mails.values());
        java.util.Collections.reverse(all);
        return all.stream().limit(limit).toList();
    }

    @Override
    public synchronized void deleteAllTo(Collection<String> addresses) {
        this.mails.values().removeIf(mail -> addresses.contains(mail.to()));
    }

    private void update(String id, UnaryOperator<OutboxMail> change) {
        this.mails.computeIfPresent(id, (key, mail) -> change.apply(mail));
    }

    private static OutboxMail with(
            OutboxMail mail,
            String id,
            MailStatus status,
            int attempts,
            Instant nextAttemptAt,
            Instant sentAt,
            Instant purgeAt) {
        return new OutboxMail(
                id,
                mail.to(),
                mail.subject(),
                mail.text(),
                mail.priority(),
                status,
                attempts,
                nextAttemptAt,
                mail.createdAt(),
                sentAt,
                purgeAt,
                mail.purgeOnSend());
    }
}
