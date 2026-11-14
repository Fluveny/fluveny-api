package com.fluveny.fluveny_backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MongoDB 8 do Testcontainers, em replica set de um nó ({@code withReplicaSet()}: sobe com {@code
 * --replSet} e inicia o set), para as transações se comportarem como no Atlas (AD-10, AD-18). A
 * {@code @ServiceConnection} vence o {@code SPRING_DATA_MONGODB_URI}. Usado pelos ITs e pelo {@link
 * LocalFluvenyApplication} ({@code mise run up}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalMongoContainer {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:8")).withReplicaSet();
    }
}
