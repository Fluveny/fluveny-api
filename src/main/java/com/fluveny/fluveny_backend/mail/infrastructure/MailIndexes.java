package com.fluveny.fluveny_backend.mail.infrastructure;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Coleção e índices de {@code mail}, criados na partida e de forma idempotente (AD-3, história
 * 2.2): em {@code mail_outbox}, o do despacho ({@code status}, {@code priority}, {@code
 * createdAt}), o da cota diária ({@code status}, {@code sentAt}) e o TTL do {@code purgeAt}, que
 * apaga o e-mail 30 dias depois de enfileirado, ainda {@code PENDING}, ou de {@code SENT} ou {@code
 * FAILED}.
 *
 * <p>Testes {@code @SpringBootTest} sem Mongo desligam com {@code
 * fluveny.mongo.indexes-on-startup=false}; o carregador local chama {@link #ensure()} de novo,
 * porque o {@code mise run reset} apaga o banco com os índices.
 */
@Component
public class MailIndexes {

    static final String DISPATCH_INDEX = "dispatch";

    static final String SENT_INDEX = "status_sentAt";

    static final String TTL_INDEX = "purgeAt_ttl";

    private final MongoTemplate mongo;

    MailIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a coleção e os índices que faltam. */
    public void ensure() {
        String collection = MongoOutboxStore.COLLECTION;
        if (!this.mongo.collectionExists(collection)) {
            this.mongo.createCollection(collection);
        }
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("priority", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.ASC)
                                .named(DISPATCH_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("sentAt", Sort.Direction.ASC)
                                .named(SENT_INDEX));
        this.mongo
                .indexOps(collection)
                .createIndex(
                        new Index()
                                .on("purgeAt", Sort.Direction.ASC)
                                .expire(Duration.ZERO)
                                .named(TTL_INDEX));
    }

    @Component
    @ConditionalOnProperty(
            name = "fluveny.mongo.indexes-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    static class OnStartup {

        private final MailIndexes indexes;

        OnStartup(MailIndexes indexes) {
            this.indexes = indexes;
        }

        @EventListener(ApplicationReadyEvent.class)
        void createIndexes() {
            this.indexes.ensure();
        }
    }
}
