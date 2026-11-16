package com.fluveny.fluveny_backend.learning.infrastructure;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Coleções e índices de {@code learning}, criados na partida e de forma idempotente (AD-3): em
 * {@code visitor_attempts}, o índice único por {@code (deviceId, activityKey)} e o TTL de 90 dias
 * em {@code createdAt} (história 1.12).
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} de novo,
 * porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class LearningIndexes {

    /** Quanto tempo um documento de {@code visitor_attempts} vive. */
    public static final Duration VISITOR_ATTEMPTS_TTL = Duration.ofDays(90);

    static final String UNIQUE_INDEX = "deviceId_activityKey";

    static final String TTL_INDEX = "createdAt_ttl";

    private final MongoTemplate mongo;

    LearningIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a coleção e os índices que faltam. */
    public void ensure() {
        String collection = MongoVisitorAttemptStore.COLLECTION;
        if (!this.mongo.collectionExists(collection)) {
            this.mongo.createCollection(collection);
        }
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("deviceId", Sort.Direction.ASC)
                                .on("activityKey", Sort.Direction.ASC)
                                .unique()
                                .named(UNIQUE_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("createdAt", Sort.Direction.ASC)
                                .expire(VISITOR_ATTEMPTS_TTL)
                                .named(TTL_INDEX));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final LearningIndexes indexes;

        OnStartup(LearningIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
