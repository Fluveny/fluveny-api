package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O Mongo local roda em replica set de um nó, então as transações se comportam como no Atlas
 * (AD-10, AD-18): o commit grava os dois documentos, e a falha no meio desfaz o que já tinha sido
 * gravado. Num Mongo standalone, o {@code MongoTransactionManager} falharia ao abrir a transação.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import({LocalMongoContainer.class, MongoTransactionIT.TransactionConfig.class})
class MongoTransactionIT {

    private static final String COLLECTION = "transaction_checks";

    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createCollection() {
        // Coleção criada fora da transação, como fará a migração de cada módulo.
        if (!this.mongoTemplate.collectionExists(COLLECTION)) {
            this.mongoTemplate.createCollection(COLLECTION);
        }
        this.mongoTemplate.remove(
                new org.springframework.data.mongodb.core.query.Query(), COLLECTION);
    }

    @Test
    void commitsBothWrites() {
        this.transactionTemplate.executeWithoutResult(status -> {
            this.mongoTemplate.insert(new Document("key", "a"), COLLECTION);
            this.mongoTemplate.insert(new Document("key", "b"), COLLECTION);
        });

        assertThat(this.mongoTemplate.getCollection(COLLECTION).countDocuments()).isEqualTo(2);
    }

    @Test
    void rollsBackTheFirstWriteWhenTheSecondFails() {
        assertThatThrownBy(() ->
                        this.transactionTemplate.executeWithoutResult(status -> {
                            this.mongoTemplate.insert(new Document("key", "a"), COLLECTION);
                            throw new IllegalStateException("falha no meio");
                        }))
                .hasMessage("falha no meio");

        assertThat(this.mongoTemplate.getCollection(COLLECTION).countDocuments()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionConfig {

        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
            return new MongoTransactionManager(databaseFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
