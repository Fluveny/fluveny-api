package com.fluveny.fluveny_backend.metrics.infrastructure;

import com.fluveny.fluveny_backend.metrics.EventType;
import java.util.Date;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code events} no Mongo. Cada evento é um documento novo; o {@code STAGE_COMPLETED} leva também o
 * {@code stageCompletionKey}, com índice único parcial ({@link MetricsIndexes}), e a duplicata,
 * inclusive concorrente, esbarra nele e é ignorada.
 */
@Repository
class MongoEventStore implements EventStore {

    static final String COLLECTION = "events";

    static final String STAGE_COMPLETION_KEY = "stageCompletionKey";

    private final MongoTemplate mongo;

    MongoEventStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean append(Event event) {
        Document document =
                new Document("type", event.type().name())
                        .append("occurredAt", Date.from(event.occurredAt()));
        putIfPresent(document, "deviceId", event.deviceId());
        putIfPresent(document, "metricsId", event.metricsId());
        if (event.fromVisitor() != null) {
            document.append("fromVisitor", event.fromVisitor());
        }
        putIfPresent(document, "unitKey", event.unitKey());
        putIfPresent(document, "versionId", event.versionId());
        putIfPresent(document, "activityKey", event.activityKey());
        putIfPresent(document, "stageKey", event.stageKey());
        putIfPresent(document, "outcome", event.outcome());
        putIfPresent(document, "activityType", event.activityType());
        if (event.resultPct() != null) {
            document.append("resultPct", event.resultPct());
        }
        if (event.firstAttempt() != null) {
            document.append("firstAttempt", event.firstAttempt());
        }
        if (event.extraHelp() != null) {
            document.append("extraHelp", event.extraHelp());
        }
        if (event.studyTimeMs() != null) {
            document.append("studyTimeMs", event.studyTimeMs());
        }
        putIfPresent(document, STAGE_COMPLETION_KEY, event.stageCompletionKey());
        try {
            this.mongo.insert(document, COLLECTION);
            return true;
        } catch (DuplicateKeyException ex) {
            if (event.type() != EventType.STAGE_COMPLETED) {
                throw ex;
            }
            // O Estágio já foi concluído por este identificador: vale o primeiro.
            return false;
        }
    }

    private static void putIfPresent(Document document, String name, String value) {
        if (value != null) {
            document.append(name, value);
        }
    }
}
