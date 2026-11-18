package com.fluveny.fluveny_backend.learning.infrastructure;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.visitor.FirstAttempt;
import com.fluveny.fluveny_backend.learning.visitor.VisitorAttemptStore;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code visitor_attempts}: um documento por {@code (deviceId, activityKey)}, com índice único
 * ({@link LearningIndexes}). Só a primeira gravação escreve ({@code $setOnInsert}); duas primeiras
 * ao mesmo tempo esbarram na chave única, e a perdedora recebe {@code false}. Os UUIDs ficam como
 * texto, sem depender da representação de UUID do driver.
 */
@Repository
class MongoVisitorAttemptStore implements VisitorAttemptStore {

    static final String COLLECTION = "visitor_attempts";

    private final MongoTemplate mongo;

    MongoVisitorAttemptStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean insertIfAbsent(FirstAttempt attempt) {
        Update update =
                new Update()
                        .setOnInsert("attemptId", attempt.attemptId().toString())
                        .setOnInsert("versionId", attempt.versionId())
                        .setOnInsert("outcome", attempt.outcome().name())
                        .setOnInsert("resultPct", attempt.resultPct())
                        .setOnInsert("xpAwarded", attempt.xpAwarded())
                        .setOnInsert("studyTimeMs", attempt.studyTimeMs())
                        .setOnInsert("nonce", attempt.nonce().toString())
                        .setOnInsert("issuedAt", Date.from(attempt.issuedAt()))
                        .setOnInsert("receipt", attempt.receipt())
                        .setOnInsert("createdAt", Date.from(attempt.createdAt()))
                        .setOnInsert("extraHelp", attempt.extraHelp());
        try {
            UpdateResult result =
                    this.mongo.upsert(
                            key(attempt.deviceId(), attempt.activityKey()), update, COLLECTION);
            return result.getUpsertedId() != null;
        } catch (DuplicateKeyException ex) {
            // Duas primeiras Tentativas ao mesmo tempo: a outra gravou primeiro.
            return false;
        }
    }

    @Override
    public Optional<FirstAttempt> find(UUID deviceId, String activityKey) {
        return Optional.ofNullable(
                        this.mongo.findOne(key(deviceId, activityKey), Document.class, COLLECTION))
                .map(MongoVisitorAttemptStore::toAttempt);
    }

    @Override
    public Set<String> recordedActivityKeys(UUID deviceId, Collection<String> activityKeys) {
        Query query =
                Query.query(
                        Criteria.where("deviceId")
                                .is(deviceId.toString())
                                .and("activityKey")
                                .in(activityKeys));
        query.fields().include("activityKey");
        Set<String> recorded = new HashSet<>();
        this.mongo
                .find(query, Document.class, COLLECTION)
                .forEach(document -> recorded.add(document.getString("activityKey")));
        return recorded;
    }

    private static Query key(UUID deviceId, String activityKey) {
        return Query.query(
                Criteria.where("deviceId")
                        .is(deviceId.toString())
                        .and("activityKey")
                        .is(activityKey));
    }

    private static FirstAttempt toAttempt(Document document) {
        return new FirstAttempt(
                UUID.fromString(document.getString("deviceId")),
                document.getString("activityKey"),
                UUID.fromString(document.getString("attemptId")),
                document.getString("versionId"),
                Outcome.valueOf(document.getString("outcome")),
                document.getInteger("resultPct"),
                document.getInteger("xpAwarded"),
                ((Number) document.get("studyTimeMs")).longValue(),
                UUID.fromString(document.getString("nonce")),
                toInstant(document.getDate("issuedAt")),
                document.getString("receipt"),
                toInstant(document.getDate("createdAt")),
                // Documentos de antes da 1.18 não têm o campo.
                Boolean.TRUE.equals(document.getBoolean("extraHelp")));
    }

    private static Instant toInstant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
