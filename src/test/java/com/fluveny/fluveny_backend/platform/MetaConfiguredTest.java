package com.fluveny.fluveny_backend.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;

/**
 * {@code GET /api/v1/meta} com a versão vinda de {@code FLUVENY_MIN_APP_VERSION}
 * e dois {@link ActivityTypeSource} de teste, na ordem. Contexto próprio, para
 * os outros testes continuarem vendo a lista vazia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MetaConfiguredTest.Sources.class)
@TestPropertySource(properties = { "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
		"FLUVENY_MIN_APP_VERSION=1.4.0" })
class MetaConfiguredTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void metaShowsTheConfiguredVersionAndTheSourcesInOrder() throws Exception {
		this.mvc.perform(get("/api/v1/meta"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.minAppVersion").value("1.4.0"))
			.andExpect(jsonPath("$.data.activityTypes.length()").value(3))
			.andExpect(jsonPath("$.data.activityTypes[0].type").value("multiple-choice"))
			.andExpect(jsonPath("$.data.activityTypes[0].sinceAppVersion").value("1.0.0"))
			.andExpect(jsonPath("$.data.activityTypes[1].type").value("fill-in"))
			.andExpect(jsonPath("$.data.activityTypes[2].type").value("speech"))
			.andExpect(jsonPath("$.data.activityTypes[2].sinceAppVersion").value("1.4.0"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Sources {

		// Registrados fora da ordem: a ordem vem do @Order.
		@Bean
		@Order(2)
		ActivityTypeSource speechSource() {
			return () -> List.of(new ActivityTypeSupport("speech", "1.4.0"));
		}

		@Bean
		@Order(1)
		ActivityTypeSource basicSource() {
			return () -> List.of(new ActivityTypeSupport("multiple-choice", "1.0.0"),
					new ActivityTypeSupport("fill-in", "1.0.0"));
		}

	}

}
