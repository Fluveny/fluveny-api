package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.account.RegisterStudent;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O Mongo local roda em replica set de um nó, então as transações se comportam como no Atlas
 * (AD-10, AD-18): o commit grava os dois documentos, e a falha no meio desfaz o que já tinha sido
 * gravado. Num Mongo standalone, o {@code MongoTransactionManager} falharia ao abrir a transação. O
 * gerenciador é o da aplicação ({@code platform.config}, história 2.6).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(LocalMongoContainer.class)
class MongoTransactionIT {

    private static final String COLLECTION = "transaction_checks";

    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private RegisterStudent registerStudent;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createCollection() {
        assertThat(this.transactionManager).isInstanceOf(MongoTransactionManager.class);
        // O @Transactional vem da auto-configuração do Boot, sem @EnableTransactionManagement.
        assertThat(AopUtils.isAopProxy(this.registerStudent)).isTrue();
        this.transactionTemplate = new TransactionTemplate(this.transactionManager);
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
}
