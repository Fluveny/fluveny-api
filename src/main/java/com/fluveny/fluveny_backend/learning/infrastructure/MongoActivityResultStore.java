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

    private final MongoTemplate mongo;

    MongoActivityResultStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean insertIfAbsent(ActivityResult result) {
        Update update =
                new Update()
                        .setOnInsert("stageKey", result.stageKey())
                        .setOnInsert("versionId", result.versionId())
                        .setOnInsert("activityType", result.activityType())
                        .setOnInsert("attemptId", result.attemptId().toString())
                        .setOnInsert("outcome", result.outcome().name())
                        .setOnInsert("xpAwarded", result.xpAwarded())
                        .setOnInsert("studyTimeMs", result.studyTimeMs())
                        .setOnInsert("extraHelp", result.extraHelp())
                        .setOnInsert("createdAt", Date.from(result.createdAt()));
        if (result.resultPct() != null) {
            update.setOnInsert("resultPct", result.resultPct());
        }
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

    private static Instant toInstant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
