package com.fluveny.fluveny_backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AD-6: nenhum componente do OpenAPI alcançável por uma rota do aluno tem propriedade secreta. Os
 * nomes secretos vêm do JSON Schema ({@code x-fluveny-secret}), não de uma lista no teste. Rota do
 * aluno é toda rota de {@code /api/v1} fora de {@code /api/v1/admin/}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class StudentRoutesOpenApiTest {

    private static final String COMPONENTS = "#/components/schemas/";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    /** Todo nome de propriedade marcada {@code x-fluveny-secret} em {@code content/schema/}. */
    static Set<String> secretNames() {
        ContentSchemas schemas =
                ContentSchemas.load(
                        Path.of(
                                System.getProperty("basedir", System.getProperty("user.dir")),
                                "content",
                                "schema"));
        Set<String> names = new TreeSet<>();
        schemas.documents().values().forEach(document -> collect(document, names));
        return names;
    }

    private static void collect(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            for (var property : node.path("properties").properties()) {
                if (property.getValue().path(ContentSchemas.SECRET).asBoolean(false)) {
                    names.add(property.getKey());
                }
            }
            node.forEach(child -> collect(child, names));
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }

    @Test
    void noComponentReachableFromAStudentRouteHasASecretProperty() throws Exception {
        Set<String> secrets = secretNames();
        assertThat(secrets).as("segredos no schema").contains("correctOptionKey", "justification");

        JsonNode api =
                MAPPER.readTree(
                        this.mvc
                                .perform(get("/v3/api-docs"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        JsonNode components = api.at("/components/schemas");

        Set<String> reachable = new TreeSet<>();
        Deque<JsonNode> pending = new ArrayDeque<>();
        int studentRoutes = 0;
        for (var path : api.path("paths").properties()) {
            if (path.getKey().startsWith("/api/v1/admin/")) {
                continue;
            }
            studentRoutes++;
            path.getValue().forEach(operation -> pending.add(operation.path("responses")));
        }
        assertThat(studentRoutes).isGreaterThanOrEqualTo(3);
        while (!pending.isEmpty()) {
            JsonNode node = pending.pop();
            if (node.isObject() && node.path("$ref").isString()) {
                String name = node.get("$ref").asString().substring(COMPONENTS.length());
                if (reachable.add(name)) {
                    pending.add(components.path(name));
                }
            }
            node.forEach(pending::add);
        }

        assertThat(reachable)
                .as("componentes das rotas do aluno")
                .contains("ChoiceActivityStudentView", "ChoiceItemStudentView")
                .doesNotContain("Activity", "ChoiceActivity", "ChoiceItem");
        TreeMap<String, Set<String>> leaked = new TreeMap<>();
        for (String name : reachable) {
            for (String property : components.path(name).path("properties").propertyNames()) {
                if (secrets.contains(property)
                        && !DISCLOSED.getOrDefault(name, Set.of()).contains(property)) {
                    leaked.computeIfAbsent(name, key -> new TreeSet<>()).add(property);
                }
            }
        }
        assertThat(leaked).as("propriedades secretas alcançáveis pelo aluno").isEmpty();
    }

    /**
     * As únicas exceções, da história 1.11 (AD-7): o Resultado da Tentativa revela o gabarito
     * depois de um Acerto ou de "Ver resposta". {@code ItemResult.justification} é a justificativa
     * do item, e {@code AttemptResult.expected} é um {@code Answer} com a opção certa de cada item
     * (o nome da propriedade não é secreto, por isso não precisa de exceção aqui, mas o conteúdo é
     * o {@code correctOptionKey}). O {@code VisitorAttemptsTest} prova que nenhum dos dois sai no
     * Erro.
     */
    private static final Map<String, Set<String>> DISCLOSED =
            Map.of("ItemResult", Set.of("justification"));

    @Test
    void theAttemptResultDisclosesOnlyTheExpectedAnswerAndTheJustification() throws Exception {
        JsonNode api =
                MAPPER.readTree(
                        this.mvc
                                .perform(get("/v3/api-docs"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        JsonNode result = api.at("/components/schemas/AttemptResult");
        assertThat(result.at("/properties/expected/$ref").asString())
                .isEqualTo(COMPONENTS + "Answer");
        assertThat(result.path("required").toString()).doesNotContain("expected");
        JsonNode item = api.at("/components/schemas/ItemResult");
        assertThat(item.path("required").toString()).doesNotContain("justification");
    }

    @Test
    void theFullActivityStillHasTheSecretsInTheContract() throws Exception {
        // Sanidade: sem isto o teste acima passaria com um contrato sem os segredos em lugar
        // nenhum, e o App não teria o par ChoiceItem/ChoiceItemStudentView para conferir os mocks.
        JsonNode api =
                MAPPER.readTree(
                        this.mvc
                                .perform(get("/v3/api-docs"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        // História 1.15: as alternativas da Fala ficam na SpeechActivity. História 1.16: o
        // gabarito do Associar fica no MatchItem.
        Set<String> full = new TreeSet<>();
        for (String name : Set.of("ChoiceItem", "SpeechActivity", "MatchItem")) {
            full.addAll(api.at("/components/schemas/" + name + "/properties").propertyNames());
        }
        assertThat(full).containsAll(secretNames());
        assertThat(
                        api.at("/components/schemas/SpeechActivityStudentView/properties")
                                .has("alternatives"))
                .isFalse();
        assertThat(api.at("/components/schemas/MatchItem/properties").propertyNames())
                .contains("correctResponseKey", "justification");
        assertThat(api.at("/components/schemas/MatchItemStudentView/properties").propertyNames())
                .containsExactlyInAnyOrder("itemKey", "line");
    }
}
