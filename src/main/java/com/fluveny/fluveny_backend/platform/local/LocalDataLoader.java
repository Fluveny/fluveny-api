package com.fluveny.fluveny_backend.platform.local;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Único ponto de carga dos dados de desenvolvimento (AD-18). Só existe no perfil {@code local}:
 * quando a API fica pronta, chama cada {@link LocalDataSeeder} dos módulos, na ordem de {@code
 * Order}. Os carregadores são idempotentes, então subir de novo não duplica nada.
 */
@Component
@Profile("local")
class LocalDataLoader {

    private static final Logger log = LoggerFactory.getLogger(LocalDataLoader.class);

    private final ObjectProvider<LocalDataSeeder> seeders;

    private final MongoTemplate mongoTemplate;

    LocalDataLoader(ObjectProvider<LocalDataSeeder> seeders, MongoTemplate mongoTemplate) {
        this.seeders = seeders;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        load();
    }

    /** Roda todos os carregadores e devolve os nomes, na ordem em que rodaram. */
    synchronized List<String> load() {
        List<String> names =
                this.seeders
                        .orderedStream()
                        .map(seeder -> {
                            log.info("Carregador local: {}", seeder.name());
                            seeder.seed();
                            return seeder.name();
                        })
                        .toList();
        log.info("Dados locais carregados ({} carregadores).", names.size());
        return names;
    }

    /** Apaga o banco inteiro e roda os carregadores de novo. */
    synchronized List<String> reset() {
        String database = this.mongoTemplate.getDb().getName();
        log.info("Apagando o banco local {}.", database);
        this.mongoTemplate.getDb().drop();
        return load();
    }
}
