package com.fluveny.fluveny_backend.learning.infrastructure;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.student.ActivityResult;
import com.fluveny.fluveny_backend.learning.student.ActivityResultStore;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code activity_results}: um documento por {@code (userId, unitKey, activityKey)}, com índice
 * único ({@link LearningIndexes}) e sem TTL (história 2.4). Só a primeira gravação escreve ({@code
 * $setOnInsert}); duas primeiras ao mesmo tempo esbarram na chave única, e a perdedora recebe
 * {@code false}. O {@code userId} e o {@code attemptId} ficam como texto; {@code resultPct} só
 * existe no {@code GRADED}.
 */
@Repository
class MongoActivityResultStore implements ActivityResultStore {

    static final String COLLECTION = "activity_results";

    /** Os campos do índice único, que o upsert grava pelo filtro. */
    private static final Set<String> KEY_FIELDS = Set.of("userId", "unitKey", "activityKey");

    private final MongoTemplate mongo;

    MongoActivityResultStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean insertIfAbsent(ActivityResult result) {
        Update update = new Update();
        toDocument(result)
                .forEach((field, value) -> {
                    // A chave fica no filtro do upsert; o resto só entra na criação.
                    if (!KEY_FIELDS.contains(field)) {
                        update.setOnInsert(field, value);
                    }
                });
        try {
            UpdateResult written =
                    this.mongo.upsert(
                            key(result.userId(), result.unitKey(), result.activityKey()),
                            update,
                            COLLECTION);
            return written.getUpsertedId() != null;
        } catch (DuplicateKeyException ex) {
            // Duas primeiras ao mesmo tempo: a outra gravou primeiro.
            return false;
        }
    }

    @Override
    public void insert(ActivityResult result) {
        this.mongo.insert(toDocument(result), COLLECTION);
    }

    /** O documento inteiro; {@code resultPct} só no {@code GRADED}. */
    private static Document toDocument(ActivityResult result) {
        Document document =
                new Document("userId", result.userId())
                        .append("unitKey", result.unitKey())
                        .append("activityKey", result.activityKey())
                        .append("stageKey", result.stageKey())
                        .append("versionId", result.versionId())
                        .append("activityType", result.activityType())
                        .append("attemptId", result.attemptId().toString())
                        .append("outcome", result.outcome().name())
                        .append("xpAwarded", result.xpAwarded())
                        .append("studyTimeMs", result.studyTimeMs())
                        .append("extraHelp", result.extraHelp())
                        .append("createdAt", Date.from(result.createdAt()));
        if (result.resultPct() != null) {
            document.append("resultPct", result.resultPct());
        }
        return document;
    }

    @Override
    public Optional<ActivityResult> find(String userId, String unitKey, String activityKey) {
        return Optional.ofNullable(
                        this.mongo.findOne(
                                key(userId, unitKey, activityKey), Document.class, COLLECTION))
                .map(MongoActivityResultStore::toResult);
    }

    @Override
    public List<ActivityResult> findByUser(String userId) {
        Query query =
                Query.query(Criteria.where("userId").is(userId))
                        .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("_id")));
        List<ActivityResult> results = new ArrayList<>();
        this.mongo
                .find(query, Document.class, COLLECTION)
                .forEach(document -> results.add(toResult(document)));
        return results;
    }

    private static Query key(String userId, String unitKey, String activityKey) {
        return Query.query(
                Criteria.where("userId")
                        .is(userId)
                        .and("unitKey")
                        .is(unitKey)
                        .and("activityKey")
                        .is(activityKey));
    }

    private static ActivityResult toResult(Document document) {
        return new ActivityResult(
                document.getString("userId"),
                document.getString("unitKey"),
                document.getString("activityKey"),
                document.getString("stageKey"),
                document.getString("versionId"),
                document.getString("activityType"),
                UUID.fromString(document.getString("attemptId")),
                Outcome.valueOf(document.getString("outcome")),
                document.getInteger("resultPct"),
                document.getInteger("xpAwarded"),
                ((Number) document.get("studyTimeMs")).longValue(),
                Boolean.TRUE.equals(document.getBoolean("extraHelp")),
                toInstant(document.getDate("createdAt")));
    }

    @Override
    public void deleteAllFor(String userId) {
        this.mongo.remove(Query.query(Criteria.where("userId").is(userId)), COLLECTION);
    }

    private static Instant toInstant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
