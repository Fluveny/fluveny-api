package com.fluveny.fluveny_backend.content.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Coleções e índices de {@code content}, criados na partida e de forma idempotente (AD-3): criar um
 * índice que já existe não muda nada. O {@code _id} das duas coleções já é o índice das buscas por
 * chave; {@code unit_versions} ganha o índice por Unidade, para listar as versões de uma.
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} antes de
 * publicar, porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class ContentIndexes {

    private final MongoTemplate mongo;

    ContentIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria as coleções e os índices que faltam. */
    public void ensure() {
        for (String collection :
                new String[] {MongoUnitVersionStore.COLLECTION, MongoUnitPointerStore.COLLECTION}) {
            if (!this.mongo.collectionExists(collection)) {
                this.mongo.createCollection(collection);
            }
        }
        this.mongo
                .indexOps(MongoUnitVersionStore.COLLECTION)
                .createIndex(
                        new Index()
                                .on("unitKey", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.ASC)
                                .named("unitKey_createdAt"));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final ContentIndexes indexes;

        OnStartup(ContentIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
