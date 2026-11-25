package com.fluveny.fluveny_backend.platform.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O {@link LocalDataLoader} roda os carregadores dos módulos quando a API sobe no perfil {@code
 * local}, e rodar de novo ({@code /local/seed}) não duplica nada. O {@code /local/reset} apaga o
 * banco e carrega de novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({LocalMongoContainer.class, LocalDataLoaderIT.SeederConfig.class})
class LocalDataLoaderIT {

    static final String COLLECTION = "local_seed_checks";

    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private LocalDataLoader loader;

    @Autowired private MockMvc mvc;

    @Test
    void seedsOnStartupAndAgainWithoutDuplicates() throws Exception {
        assertThat(count()).as("carregado na partida").isEqualTo(2);

        // Os carregadores de content (@Order(100)), account (@Order(150)), auth (@Order(160)),
        // mail (@Order(170)), learning (@Order(200)) e metrics (@Order(300)) rodam depois dos
        // dois de teste.
        assertThat(this.loader.load())
                .containsSubsequence("first", "second", "content")
                .endsWith("account", "auth", "mail", "learning", "metrics");
        this.mvc
                .perform(post("/local/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seeders[0]").value("first"))
                .andExpect(jsonPath("$.data.seeders[1]").value("second"));

        assertThat(count()).as("sem duplicar").isEqualTo(2);
    }

    @Test
    void resetDropsTheDatabaseAndSeedsAgain() throws Exception {
        this.mongoTemplate.insert(new Document("key", "lixo"), "leftovers");

        this.mvc
                .perform(post("/local/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Banco apagado e carregadores executados."));

        assertThat(this.mongoTemplate.collectionExists("leftovers")).isFalse();
        assertThat(count()).isEqualTo(2);
        // O carregador de learning recria os índices de visitor_attempts (história 1.12).
        List<String> indexes = new ArrayList<>();
        this.mongoTemplate
                .getCollection("visitor_attempts")
                .listIndexes()
                .map(index -> index.getString("name"))
                .into(indexes);
        assertThat(indexes).contains("deviceId_activityKey", "createdAt_ttl");
        // O de metrics recria o de events (história 1.20).
        List<String> eventIndexes = new ArrayList<>();
        this.mongoTemplate
                .getCollection("events")
                .listIndexes()
                .map(index -> index.getString("name"))
                .into(eventIndexes);
        assertThat(eventIndexes).contains("stageCompletionKey_unique");
        // Os de account e auth recriam os de users e sessions (história 2.1).
        List<String> userIndexes = new ArrayList<>();
        this.mongoTemplate
                .getCollection("users")
                .listIndexes()
                .map(index -> index.getString("name"))
                .into(userIndexes);
        assertThat(userIndexes).contains("email_unique", "metricsId_unique");
        List<String> sessionIndexes = new ArrayList<>();
        this.mongoTemplate
                .getCollection("sessions")
                .listIndexes()
                .map(index -> index.getString("name"))
                .into(sessionIndexes);
        assertThat(sessionIndexes).contains("tokenHash_unique", "expiresAt_ttl");
        // E os quatro Alunos de teste voltam (o desativado entrou na 2.3; o atrasado, na 2.8).
        assertThat(this.mongoTemplate.getCollection("users").countDocuments()).isEqualTo(4);
    }

    private long count() {
        return this.mongoTemplate.getCollection(COLLECTION).countDocuments();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SeederConfig {

        @Bean
        @Order(2)
        LocalDataSeeder second(MongoTemplate mongoTemplate) {
            return new KeyedSeeder("second", mongoTemplate);
        }

        @Bean
        @Order(1)
        LocalDataSeeder first(MongoTemplate mongoTemplate) {
            return new KeyedSeeder("first", mongoTemplate);
        }
    }

    /** Carregador idempotente como os dos módulos: grava por chave, com upsert. */
    record KeyedSeeder(String name, MongoTemplate mongoTemplate) implements LocalDataSeeder {

        @Override
        public void seed() {
            this.mongoTemplate.upsert(
                    Query.query(Criteria.where("key").is(this.name)),
                    Update.update("key", this.name),
                    COLLECTION);
        }
    }
}
