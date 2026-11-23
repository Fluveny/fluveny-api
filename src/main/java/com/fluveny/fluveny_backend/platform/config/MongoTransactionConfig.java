package com.fluveny.fluveny_backend.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * O gerenciador de transações do Mongo (AD-10, história 2.6); o {@code @Transactional} vem ligado
 * pela auto-configuração de transações do Boot. Só o cadastro abre transação: a conta, a sessão e a
 * migração do Visitante gravam juntas ou nada grava. O Mongo local, o dos {@code *IT} ({@code
 * LocalMongoContainer}) e o Atlas rodam em replica set; num standalone a transação não abre.
 *
 * <p>As coleções que entram numa transação são criadas na partida, pelos índices de cada módulo.
 * Desde o MongoDB 4.4 a transação pode criar a coleção, mas ela nasceria sem os índices únicos dos
 * quais a deduplicação depende, e duas transações que a criam ao mesmo tempo dão conflito.
 */
@Configuration(proxyBeanMethods = false)
class MongoTransactionConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
