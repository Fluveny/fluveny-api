package com.fluveny.fluveny_backend.auth.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code login_throttle} no Mongo, com o índice único do {@code keyHash} e o TTL do {@code
 * expiresAt} ({@link AuthIndexes}). Cada documento tem só {@code keyHash}, {@code kind}, {@code
 * failures}, {@code lastFailureAt} e {@code expiresAt}.
 */
@Repository
class MongoLoginThrottleStore implements LoginThrottleStore {

    static final String COLLECTION = "login_throttle";

    private final MongoTemplate mongo;

    MongoLoginThrottleStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<ThrottleEntry> find(Collection<String> keyHashes) {
        return this.mongo
                .find(
                        Query.query(Criteria.where("keyHash").in(keyHashes)),
                        Document.class,
                        COLLECTION)
                .stream()
                .map(document ->
                        new ThrottleEntry(
                                document.getString("keyHash"),
                                document.getInteger("failures", 0),
                                instant(document.getDate("lastFailureAt")),
                                instant(document.getDate("expiresAt"))))
                .toList();
    }

    @Override
    public void recordFailure(String keyHash, String kind, Instant now, Instant expiresAt) {
        // O vencido conta como zero: o TTL do Mongo roda só a cada minuto.
        this.mongo.remove(
                Query.query(
                        Criteria.where("keyHash").is(keyHash).and("expiresAt").lte(Date.from(now))),
                COLLECTION);
        Update update =
                new Update()
                        .inc("failures", 1)
                        .set("lastFailureAt", Date.from(now))
                        .set("expiresAt", Date.from(expiresAt))
                        .setOnInsert("kind", kind);
        Query query = Query.query(Criteria.where("keyHash").is(keyHash));
        try {
            this.mongo.upsert(query, update, COLLECTION);
        } catch (DuplicateKeyException ex) {
            // Duas falhas da mesma chave ao mesmo tempo: a outra criou o documento.
            this.mongo.upsert(query, update, COLLECTION);
        }
    }

    @Override
    public void delete(Collection<String> keyHashes) {
        this.mongo.remove(Query.query(Criteria.where("keyHash").in(keyHashes)), COLLECTION);
    }

    private static Instant instant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
