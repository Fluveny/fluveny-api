package com.fluveny.fluveny_backend.mail.infrastructure;

import com.fluveny.fluveny_backend.mail.MailPriority;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code mail_outbox} no Mongo, com os índices de {@link MailIndexes}. A {@code priority} fica como
 * o nome do enum, e a ordem do despacho é a alfabética: {@code HIGH} vem antes de {@code NORMAL}.
 * Prioridade nova precisa manter essa ordem (ou trocar o campo por um número).
 */
@Repository
class MongoOutboxStore implements OutboxStore {

    static final String COLLECTION = "mail_outbox";

    private final MongoTemplate mongo;

    MongoOutboxStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public String insert(OutboxMail mail) {
        ObjectId id = new ObjectId();
        this.mongo.insert(
                new Document("_id", id)
                        .append("to", mail.to())
                        .append("subject", mail.subject())
                        .append("text", mail.text())
                        .append("priority", mail.priority().name())
                        .append("status", mail.status().name())
                        .append("attempts", mail.attempts())
                        .append("nextAttemptAt", date(mail.nextAttemptAt()))
                        .append("createdAt", date(mail.createdAt()))
                        .append("sentAt", date(mail.sentAt()))
                        .append("purgeAt", date(mail.purgeAt())),
                COLLECTION);
        return id.toHexString();
    }

    @Override
    public Optional<OutboxMail> claimNext(Instant now, Instant leaseUntil) {
        Query query =
                Query.query(
                                Criteria.where("status")
                                        .is(MailStatus.PENDING.name())
                                        .and("nextAttemptAt")
                                        .lte(Date.from(now)))
                        .with(
                                Sort.by(
                                        Sort.Order.asc("priority"),
                                        Sort.Order.asc("createdAt"),
                                        Sort.Order.asc("_id")));
        return Optional.ofNullable(
                        this.mongo.findAndModify(
                                query,
                                Update.update("nextAttemptAt", Date.from(leaseUntil)),
                                FindAndModifyOptions.options().returnNew(false),
                                Document.class,
                                COLLECTION))
                .map(MongoOutboxStore::toMail);
    }

    @Override
    public void markSent(String id, int attempts, Instant sentAt, Instant purgeAt) {
        this.mongo.updateFirst(
                byId(id),
                new Update()
                        .set("status", MailStatus.SENT.name())
                        .set("attempts", attempts)
                        .set("sentAt", Date.from(sentAt))
                        .set("purgeAt", Date.from(purgeAt)),
                COLLECTION);
    }

    @Override
    public void markRetry(String id, int attempts, Instant nextAttemptAt) {
        this.mongo.updateFirst(
                byId(id),
                new Update()
                        .set("attempts", attempts)
                        .set("nextAttemptAt", Date.from(nextAttemptAt)),
                COLLECTION);
    }

    @Override
    public void markFailed(String id, int attempts, Instant purgeAt) {
        this.mongo.updateFirst(
                byId(id),
                new Update()
                        .set("status", MailStatus.FAILED.name())
                        .set("attempts", attempts)
                        .set("purgeAt", Date.from(purgeAt)),
                COLLECTION);
    }

    @Override
    public long countSentSince(Instant since) {
        return this.mongo.count(
                Query.query(
                        Criteria.where("status")
                                .is(MailStatus.SENT.name())
                                .and("sentAt")
                                .gte(Date.from(since))),
                COLLECTION);
    }

    @Override
    public List<OutboxMail> latest(int limit) {
        return this.mongo
                .find(
                        new Query()
                                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                                .limit(limit),
                        Document.class,
                        COLLECTION)
                .stream()
                .map(MongoOutboxStore::toMail)
                .toList();
    }

    private static Query byId(String id) {
        return Query.query(Criteria.where("_id").is(new ObjectId(id)));
    }

    private static OutboxMail toMail(Document document) {
        return new OutboxMail(
                document.getObjectId("_id").toHexString(),
                document.getString("to"),
                document.getString("subject"),
                document.getString("text"),
                MailPriority.valueOf(document.getString("priority")),
                MailStatus.valueOf(document.getString("status")),
                document.getInteger("attempts", 0),
                instant(document.getDate("nextAttemptAt")),
                instant(document.getDate("createdAt")),
                instant(document.getDate("sentAt")),
                instant(document.getDate("purgeAt")));
    }

    private static Date date(Instant instant) {
        return (instant == null) ? null : Date.from(instant);
    }

    private static Instant instant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
