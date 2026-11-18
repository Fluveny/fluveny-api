package com.fluveny.fluveny_backend.metrics.infrastructure;

import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Coleção e índices de {@code metrics}, criados na partida e de forma idempotente (AD-14): em
 * {@code events}, o índice único parcial do {@code stageCompletionKey}, que só o {@code
 * STAGE_COMPLETED} tem (história 1.20).
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} de novo,
 * porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class MetricsIndexes {

    static final String STAGE_COMPLETION_INDEX = "stageCompletionKey_unique";

    private final MongoTemplate mongo;

    MetricsIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a coleção e os índices que faltam. */
    public void ensure() {
        String collection = MongoEventStore.COLLECTION;
        if (!this.mongo.collectionExists(collection)) {
            this.mongo.createCollection(collection);
        }
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on(MongoEventStore.STAGE_COMPLETION_KEY, Sort.Direction.ASC)
                                .unique()
                                .partial(
                                        PartialIndexFilter.of(
                                                new Document(
                                                        MongoEventStore.STAGE_COMPLETION_KEY,
                                                        new Document("$exists", true))))
                                .named(STAGE_COMPLETION_INDEX));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final MetricsIndexes indexes;

        OnStartup(MetricsIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
