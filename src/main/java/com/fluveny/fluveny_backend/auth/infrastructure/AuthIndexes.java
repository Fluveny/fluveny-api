package com.fluveny.fluveny_backend.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Coleção e índices de {@code auth}, criados na partida e de forma idempotente (AD-3): em {@code
 * sessions}, o único do {@code tokenHash} e o TTL do {@code expiresAt}, que apaga a sessão quando
 * ela vence (história 2.1). A conferência do vencimento não depende do TTL, que roda a cada minuto.
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} de novo,
 * porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class AuthIndexes {

    static final String TOKEN_HASH_INDEX = "tokenHash_unique";

    static final String TTL_INDEX = "expiresAt_ttl";

    private final MongoTemplate mongo;

    AuthIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a coleção e os índices que faltam. */
    public void ensure() {
        String collection = MongoSessionStore.COLLECTION;
        if (!this.mongo.collectionExists(collection)) {
            this.mongo.createCollection(collection);
        }
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("tokenHash", Sort.Direction.ASC)
                                .unique()
                                .named(TOKEN_HASH_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("expiresAt", Sort.Direction.ASC)
                                .expire(Duration.ZERO)
                                .named(TTL_INDEX));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final AuthIndexes indexes;

        OnStartup(AuthIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
