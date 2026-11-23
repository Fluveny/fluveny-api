package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe o contexto inteiro contra o MongoDB 8 do {@link LocalMongoContainer} (NFR13). Não precisa de
 * {@code SPRING_DATA_MONGODB_URI}: a {@code @ServiceConnection} vence a propriedade. Por isso roda
 * fora do perfil {@code prod}, que exige a variável; o {@code MongoUriBindingIT} sobe o contexto no
 * {@code prod}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class ApplicationIT {

    @Autowired private MongoTemplate mongo;

    @Test
    void contextLoads() {}

    /**
     * Os índices de {@code visitor_attempts} vêm do {@code LearningIndexes.OnStartup} (história
     * 1.12), fora do perfil local. Nenhum teste deste contexto apaga a coleção.
     */
    @Test
    void createsTheVisitorAttemptsIndexesOnStartup() {
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("visitor_attempts").listIndexes().into(indexes);
        Document unique = byName(indexes, "deviceId_activityKey");
        assertThat(unique.getBoolean("unique")).isTrue();
        Document ttl = byName(indexes, "createdAt_ttl");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue())
                .isEqualTo(90L * 24 * 60 * 60);
    }

    /** O índice único de {@code activity_results}, sem TTL, também da partida (história 2.4). */
    @Test
    void createsTheActivityResultsIndexOnStartup() {
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("activity_results").listIndexes().into(indexes);
        Document unique = byName(indexes, "userId_unitKey_activityKey");
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.containsKey("expireAfterSeconds")).isFalse();
    }

    /** O índice único parcial de {@code events} vem do {@code MetricsIndexes.OnStartup} (1.20). */
    @Test
    void createsTheEventsIndexOnStartup() {
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("events").listIndexes().into(indexes);
        Document unique = byName(indexes, "stageCompletionKey_unique");
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("partialFilterExpression")).isNotNull();
    }

    private static Document byName(List<Document> indexes, String name) {
        return indexes.stream()
                .filter(index -> name.equals(index.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Índice ausente: " + name));
    }
}
