package com.fluveny.fluveny_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe o contexto inteiro contra um MongoDB 8 do Testcontainers (NFR13).
 * Não precisa de {@code SPRING_DATA_MONGODB_URI}: a {@code @ServiceConnection}
 * vence a propriedade.
 */
@SpringBootTest
@Import(ApplicationIT.MongoContainerConfig.class)
class ApplicationIT {

	@Test
	void contextLoads() {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MongoContainerConfig {

		@Bean
		@ServiceConnection
		MongoDBContainer mongoDbContainer() {
			return new MongoDBContainer(DockerImageName.parse("mongo:8"));
		}

	}

}
