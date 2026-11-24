package com.fluveny.fluveny_backend.learning.infrastructure;

import com.fluveny.fluveny_backend.learning.student.UnitProgress;
import com.fluveny.fluveny_backend.learning.student.UnitProgressStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code unit_progress}: um documento por {@code (userId, unitKey)}, com índice único ({@link
 * LearningIndexes}) e sem TTL (história 2.5). As conclusões ficam em {@code stages.<stageKey>:
 * {versionId, completedAt}}; a {@code stageKey} é kebab-case, então serve de nome de campo.
 *
 * <p>A gravação é condicional ao Estágio ainda não estar lá: com o documento, o {@code update} só
 * casa sem a conclusão; sem ele, o {@code upsert} cria. Dois {@code upsert}s ao mesmo tempo
 * esbarram na chave única, e o perdedor tenta de novo sobre o documento que o outro criou (pode ser
 * de outro Estágio).
 */
@Repository
class MongoUnitProgressStore implements UnitProgressStore {

    static final String COLLECTION = "unit_progress";

    private final MongoTemplate mongo;

    MongoUnitProgressStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean completeStage(
            String userId, String unitKey, String stageKey, String versionId, Instant completedAt) {
        String field = "stages." + stageKey;
        Query pending =
                Query.query(
                        Criteria.where("userId")
                                .is(userId)
                                .and("unitKey")
                                .is(unitKey)
                                .and(field)
                                .exists(false));
        Update update =
                new Update()
                        .set(field + ".versionId", versionId)
                        .set(field + ".completedAt", Date.from(completedAt));
        try {
            var written = this.mongo.upsert(pending, update, COLLECTION);
            return written.getUpsertedId() != null || written.getModifiedCount() > 0;
        } catch (DuplicateKeyException ex) {
            // O documento nasceu entre a busca e o insert: sem upsert, vale a condição.
            return this.mongo.updateFirst(pending, update, COLLECTION).getModifiedCount() > 0;
        }
    }

    @Override
    public List<UnitProgress> findByUser(String userId) {
        List<UnitProgress> found = new ArrayList<>();
        this.mongo
                .find(Query.query(Criteria.where("userId").is(userId)), Document.class, COLLECTION)
                .forEach(document -> found.add(toProgress(document)));
        return found;
    }

    @Override
    public void deleteAllFor(String userId) {
        this.mongo.remove(Query.query(Criteria.where("userId").is(userId)), COLLECTION);
    }

    private static UnitProgress toProgress(Document document) {
        Map<String, UnitProgress.StageCompletion> stages = new LinkedHashMap<>();
        Document stored = document.get("stages", Document.class);
        if (stored != null) {
            stored.forEach((stageKey, value) -> {
                Document completion = (Document) value;
                Date completedAt = completion.getDate("completedAt");
                stages.put(
                        stageKey,
                        new UnitProgress.StageCompletion(
                                completion.getString("versionId"),
                                (completedAt == null) ? null : completedAt.toInstant()));
            });
        }
        return new UnitProgress(
                document.getString("userId"), document.getString("unitKey"), stages);
    }
}
