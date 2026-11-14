package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova que a API se conecta pela variável {@code SPRING_DATA_MONGODB_URI}
 * (NFR1). Sem {@code @ServiceConnection}: a URI do container chega como essa
 * propriedade e passa pelo {@code application.properties}.
 */
@SpringBootTest
class MongoUriBindingIT {

	private static final String DATABASE = "uri_binding_check";

	// Sobe uma vez por JVM; o Ryuk do Testcontainers derruba o container no fim.
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8"));

	static {
		MONGO.start();
	}

	@DynamicPropertySource
	static void mongoUri(DynamicPropertyRegistry registry) {
		registry.add("SPRING_DATA_MONGODB_URI", () -> MONGO.getReplicaSetUrl(DATABASE));
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void connectsThroughTheEnvironmentVariable() {
		Document pong = mongoTemplate.executeCommand(new Document("ping", 1));

		assertThat(pong.get("ok", Number.class).doubleValue()).isEqualTo(1.0);
		assertThat(mongoTemplate.getDb().getName()).isEqualTo(DATABASE);
	}

}
