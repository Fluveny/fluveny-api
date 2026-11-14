package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * O contrato commitado em {@code openapi/openapi.json} é o {@code /v3/api-docs}
 * da API (AD-11). O teste só compara; com {@code -Dopenapi.write=true}, grava o
 * arquivo. Sem os controllers de teste: o contrato é só o da aplicação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests")
class OpenApiContractTest {

	/** Resolvido pelo {@code basedir} do Maven, não pelo diretório de trabalho. */
	static final Path CONTRACT = Path.of(System.getProperty("basedir", System.getProperty("user.dir")), "openapi",
			"openapi.json");

	static final String REGENERATE = "./mvnw -B -ntp test -Dtest=OpenApiContractTest -Dopenapi.write=true";

	/** Chaves ordenadas em todos os níveis e indentação fixa, com quebra de linha no fim. */
	private static final JsonMapper NORMALIZER = JsonMapper.builder()
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.enable(SerializationFeature.INDENT_OUTPUT)
		.build();

	@Autowired
	private MockMvc mvc;

	@Test
	void committedContractMatchesTheApiDocs() throws Exception {
		String body = this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		String generated = normalize(body);

		if (Boolean.getBoolean("openapi.write")) {
			Files.createDirectories(CONTRACT.getParent());
			Files.writeString(CONTRACT, generated, StandardCharsets.UTF_8);
			return;
		}

		assertThat(CONTRACT).as("O contrato %s não existe. Gere com: %s", CONTRACT, REGENERATE).exists();
		String committed = Files.readString(CONTRACT, StandardCharsets.UTF_8);
		assertThat(committed)
			.as("O %s está diferente do /v3/api-docs. Não edite o arquivo à mão: gere de novo com %s e commite o"
					+ " resultado. Mudança no /api/v1 precisa ser aditiva (o oasdiff da CI confere).", CONTRACT,
					REGENERATE)
			.isEqualTo(generated);
	}

	@Test
	void contractIsOpenApi31WithFixedServer() throws Exception {
		String body = this.mvc.perform(get("/v3/api-docs"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		var tree = NORMALIZER.readTree(body);
		assertThat(tree.path("openapi").asString()).startsWith("3.1");
		assertThat(tree.path("servers").size()).isEqualTo(1);
		assertThat(tree.path("servers").path(0).path("url").asString()).isEqualTo("/");
	}

	/**
	 * O contrato só tem o {@code /api/v1} da aplicação: nenhum path de fora e
	 * nenhum {@code operationId} ou tag de controller de teste (como os de
	 * {@link PlatformTestRoutes}), mesmo que algum vaze para o contexto.
	 */
	@Test
	void contractOnlyHasApplicationApiV1Routes() throws Exception {
		String body = this.mvc.perform(get("/v3/api-docs"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		var paths = NORMALIZER.readTree(body).path("paths");
		assertThat(paths.size()).isPositive();

		List<String> testNames = new ArrayList<>();
		testNames.add(PlatformTestRoutes.class.getSimpleName());
		for (Class<?> nested : PlatformTestRoutes.class.getDeclaredClasses()) {
			testNames.add(nested.getSimpleName());
		}
		List<String> forbidden = testNames.stream()
			// O nome em kebab sozinho ("roles", "open") pode virar tag legítima de
			// um módulo; o guarda forte é o prefixo /api/v1 (as rotas de teste são /test/**).
			.flatMap(name -> Stream.of(name, kebab(name) + "-controller", "platform-test-routes"))
			.map(name -> name.toLowerCase(Locale.ROOT))
			.distinct()
			.toList();

		for (var path : paths.properties()) {
			assertThat(path.getKey()).as("path fora do /api/v1 no contrato").startsWith("/api/v1/");
			for (var operation : path.getValue().properties()) {
				List<String> names = new ArrayList<>();
				names.add(operation.getValue().path("operationId").asString());
				operation.getValue().path("tags").forEach(tag -> names.add(tag.asString()));
				for (String name : names) {
					String lower = name.toLowerCase(Locale.ROOT);
					assertThat(lower).as("%s %s vem de classe de teste", operation.getKey(), path.getKey())
						.doesNotContain("test")
						.isNotIn(forbidden);
				}
			}
		}
	}

	private static String kebab(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
	}

	static String normalize(String json) {
		Object value = NORMALIZER.readValue(json, Object.class);
		return NORMALIZER.writeValueAsString(value).replace("\r\n", "\n") + "\n";
	}

}
