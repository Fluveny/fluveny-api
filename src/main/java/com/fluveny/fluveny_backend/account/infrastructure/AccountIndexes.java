package com.fluveny.fluveny_backend.account.infrastructure;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Coleção e índices de {@code account}, criados na partida e de forma idempotente (AD-3): em {@code
 * users}, o único do {@code email} (decide o e-mail repetido, inclusive em cadastros simultâneos) e
 * o único do {@code metricsId} (história 2.1); em {@code one_time_tokens}, o único do {@code
 * tokenHash}, o do {@code userId} (o reenvio apaga os anteriores) e o TTL do {@code expiresAt}
 * (história 2.2). A conferência do vencimento não depende do TTL, que roda a cada minuto.
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} de novo,
 * porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class AccountIndexes {

    static final String EMAIL_INDEX = "email_unique";

    static final String METRICS_ID_INDEX = "metricsId_unique";

    static final String TOKEN_HASH_INDEX = "tokenHash_unique";

    static final String TOKEN_USER_INDEX = "userId_purpose";

    static final String TOKEN_TTL_INDEX = "expiresAt_ttl";

    private final MongoTemplate mongo;

    AccountIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a coleção e os índices que faltam. */
    public void ensure() {
        String collection = MongoUserStore.COLLECTION;
        if (!this.mongo.collectionExists(collection)) {
            this.mongo.createCollection(collection);
        }
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index().on("email", Sort.Direction.ASC).unique().named(EMAIL_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("metricsId", Sort.Direction.ASC)
                                .unique()
                                .named(METRICS_ID_INDEX));
        ensureTokens();
    }

    private void ensureTokens() {
        String collection = MongoOneTimeTokenStore.COLLECTION;
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
                                .on("userId", Sort.Direction.ASC)
                                .on("purpose", Sort.Direction.ASC)
                                .named(TOKEN_USER_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("expiresAt", Sort.Direction.ASC)
                                .expire(Duration.ZERO)
                                .named(TOKEN_TTL_INDEX));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final AccountIndexes indexes;

        OnStartup(AccountIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
