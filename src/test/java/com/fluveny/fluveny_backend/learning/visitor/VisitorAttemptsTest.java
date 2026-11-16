package com.fluveny.fluveny_backend.learning.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.content.publication.UnitPointer;
import com.fluveny.fluveny_backend.content.publication.UnitPointerStore;
import com.fluveny.fluveny_backend.content.publication.UnitVersion;
import com.fluveny.fluveny_backend.content.publication.UnitVersionStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * As rotas de Tentativa e "Ver resposta" do Visitante (história 1.11), sem Mongo: as coleções de
 * {@code content} ficam em memória, e o conteúdo entra pelo mesmo {@link PublishUnitVersion} da
 * rota admin. A fixture {@code unit-1} tem {@code s1-greetings} (2 itens, xp 10) no Estágio 1 e
 * {@code s2-flags} no Estágio 2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(VisitorAttemptsTest.InMemoryContent.class)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class VisitorAttemptsTest {

    private static final String ATTEMPTS = "/api/v1/visitor/attempts";

    private static final String REVEAL = ATTEMPTS + "/reveal";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private PublishUnitVersion publisher;

    @Autowired private InMemoryContent content;

    private String versionId;

    @BeforeEach
    void publish() throws Exception {
        this.content.clear();
        this.versionId = publish(validBundle());
    }

    private String publish(JsonNode bundle) {
        return this.publisher.publish("unit-1", bundle, true, TypeCheck.SKIP).versionId();
    }

    static ObjectNode validBundle() throws Exception {
        Path root =
                Path.of(VisitorAttemptsTest.class.getResource("/content-fixtures/valid").toURI());
        return UnitBundle.fromDirectory(root.resolve("units/unit-1"), root.resolve("media.json"));
    }

    private ObjectNode attempt(String activityKey, String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("type", "choice");
        ObjectNode answer = body.putObject("answer");
        answer.put("type", "choice");
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < pairs.length; i += 2) {
            items.addObject().put("itemKey", pairs[i]).put("optionKey", pairs[i + 1]);
        }
        body.put("studyTimeMs", 1200);
        return body;
    }

    private ObjectNode allRight() {
        return attempt(
                "s1-greetings",
                "s1-greetings-hello",
                "nice-to-meet-you-too",
                "s1-greetings-pronoun",
                "yes");
    }

    private ObjectNode halfRight() {
        return attempt(
                "s1-greetings", "s1-greetings-hello", "goodbye", "s1-greetings-pronoun", "yes");
    }

    private ResultActions send(String path, JsonNode body) throws Exception {
        return this.mvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return MAPPER.readTree(
                result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void aHitReturnsTheExpectedAnswerAndTheRawJustifications() throws Exception {
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].itemKey").value("s1-greetings-hello"))
                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos com **too** para devolver o cumprimento."))
                .andExpect(
                        jsonPath("$.data.items[1].justification.they")
                                .value("They **are** a student."))
                .andExpect(jsonPath("$.data.expected.type").value("choice"))
                .andExpect(jsonPath("$.data.expected.items[0].itemKey").value("s1-greetings-hello"))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"))
                .andExpect(jsonPath("$.data.expected.items[1].optionKey").value("yes"));
    }

    @Test
    void aMissNeverReturnsTheExpectedAnswerNorAJustification() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, halfRight())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(50))
                                .andExpect(jsonPath("$.data.xpAwarded").value(5))
                                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                                .andExpect(jsonPath("$.data.items[1].correct").value(true)));

        assertThat(keys(body)).doesNotContain("expected", "justification", "optionKey");
        assertThat(body.toString()).doesNotContain("nice-to-meet-you-too", "**too**");
    }

    @Test
    void revealGradesTheSameAttemptAndDisclosesWithoutXp() throws Exception {
        send(REVEAL, halfRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(50))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos com **too** para devolver o cumprimento."))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"));
    }

    @Test
    void theSameAttemptGivesTheSameResult() throws Exception {
        ObjectNode attempt = halfRight();
        String first = json(send(ATTEMPTS, attempt)).toString();
        assertThat(json(send(ATTEMPTS, attempt)).toString()).isEqualTo(first);
    }

    @Test
    void anOlderVersionIsStillGradedAgainstItself() throws Exception {
        String old = this.versionId;
        ObjectNode changed = validBundle();
        ((ObjectNode) changed.at("/stages/0/activities/0/items/0"))
                .put("correctOptionKey", "goodbye");
        String current = publish(changed);
        assertThat(current).isNotEqualTo(old);

        // Contra a versão antiga, "nice-to-meet-you-too" continua certo.
        send(ATTEMPTS, allRight()).andExpect(jsonPath("$.data.resultPct").value(100));
        this.versionId = current;
        send(ATTEMPTS, allRight()).andExpect(jsonPath("$.data.resultPct").value(50));
    }

    @Test
    void anActivityOutsideTheFirstStageIsUnauthenticated() throws Exception {
        ObjectNode attempt = attempt("s2-flags", "s2-flags-brazil", "brazil");
        expectProblem(send(ATTEMPTS, attempt), 401, "UNAUTHENTICATED");
        expectProblem(send(REVEAL, attempt), 401, "UNAUTHENTICATED");
    }

    @Test
    void aUnitThatIsNotTheFirstPublishedIsUnauthenticated() throws Exception {
        // Mesmo order: o desempate é pelo unitKey, e unit-0 passa a ser a primeira.
        ObjectNode first = MAPPER.createObjectNode();
        first.putObject("unit")
                .put("unitKey", "unit-0")
                .put("order", 1)
                .put("schemaVersion", 1)
                .put("title", "Coming soon")
                .<ObjectNode>set("stages", MAPPER.createArrayNode())
                .set("usefulExpressions", MAPPER.createArrayNode());
        first.putArray("stages");
        first.putObject("media");
        this.publisher.publish("unit-0", first, true, TypeCheck.SKIP);

        expectProblem(send(ATTEMPTS, allRight()), 401, "UNAUTHENTICATED");
    }

    @Test
    void unknownVersionOrActivityIsNotFound() throws Exception {
        ObjectNode unknownActivity = allRight().put("activityKey", "s1-nothing");
        expectProblem(send(ATTEMPTS, unknownActivity), 404, "NOT_FOUND");
        ObjectNode unknownVersion = allRight().put("versionId", "sha256:" + "0".repeat(64));
        expectProblem(send(ATTEMPTS, unknownVersion), 404, "NOT_FOUND");
        expectProblem(send(REVEAL, unknownVersion), 404, "NOT_FOUND");
    }

    @Test
    void anIncompleteOrRepeatedAnswerIsInvalid() throws Exception {
        ObjectNode missing = attempt("s1-greetings", "s1-greetings-hello", "goodbye");
        expectProblem(send(ATTEMPTS, missing), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, missing), 400, "INVALID_ANSWER");

        ObjectNode repeated =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-pronoun",
                        "yes");
        expectProblem(send(ATTEMPTS, repeated), 400, "INVALID_ANSWER");

        ObjectNode unknownOption =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "maybe",
                        "s1-greetings-pronoun",
                        "yes");
        expectProblem(send(ATTEMPTS, unknownOption), 400, "INVALID_ANSWER");

        ObjectNode unknownItem =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-other",
                        "yes");
        expectProblem(send(ATTEMPTS, unknownItem), 400, "INVALID_ANSWER");
    }

    @Test
    void anAttemptOfAnotherTypeIsInvalid() throws Exception {
        expectProblem(send(ATTEMPTS, allRight().put("type", "order")), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, allRight().put("type", "order")), 400, "INVALID_ANSWER");
    }

    @Test
    void aNullAnswerItemIsInvalid() throws Exception {
        ObjectNode body = allRight();
        ((ArrayNode) body.at("/answer/items")).addNull();
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
    }

    @Test
    void revealOfAHitStillAwardsNoXp() throws Exception {
        send(REVEAL, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "attemptId=not-a-uuid",
                "versionId=sha256:abc",
                "studyTimeMs=-1",
                "attemptId=",
                "answer=",
                "type=",
                "type= ",
                "activityKey=long-key",
                "answer.type=unknown-type"
            })
    void aMalformedBodyIs400(String change) throws Exception {
        ObjectNode body = allRight();
        String[] parts = change.split("=", 2);
        if (parts[0].equals("answer.type")) {
            ((ObjectNode) body.get("answer")).put("type", parts[1]);
        } else if (parts[0].equals("studyTimeMs")) {
            body.put("studyTimeMs", Long.parseLong(parts[1]));
        } else if (parts[1].equals("long-key")) {
            body.put(parts[0], "a".repeat(201));
        } else if (parts[1].isEmpty()) {
            body.remove(parts[0]);
        } else {
            body.put(parts[0], parts[1]);
        }
        int status = send(ATTEMPTS, body).andReturn().getResponse().getStatus();
        assertThat(status).as(change).isEqualTo(400);
        expectProblem(send(ATTEMPTS, body), 400, "VALIDATION_FAILED");
    }

    private static java.util.Set<String> keys(JsonNode node) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        collect(node, keys);
        return keys;
    }

    private static void collect(JsonNode node, java.util.Set<String> keys) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                keys.add(property.getKey());
                collect(property.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(element -> collect(element, keys));
        }
    }

    /** {@code unit_versions} e {@code unit_pointers} em memória. */
    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryContent {

        private final Map<String, UnitVersion> versions = new ConcurrentHashMap<>();

        private final Map<String, UnitPointer> pointers = new ConcurrentHashMap<>();

        void clear() {
            this.versions.clear();
            this.pointers.clear();
        }

        @Bean
        @Primary
        UnitVersionStore inMemoryVersions() {
            return new UnitVersionStore() {
                @Override
                public boolean insertIfAbsent(UnitVersion version) {
                    return InMemoryContent.this.versions.putIfAbsent(version.id(), version) == null;
                }

                @Override
                public Optional<UnitVersion> findById(String id) {
                    return Optional.ofNullable(InMemoryContent.this.versions.get(id));
                }

                @Override
                public List<UnitVersion> findAllById(Collection<String> ids) {
                    return ids.stream()
                            .map(InMemoryContent.this.versions::get)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                }
            };
        }

        @Bean
        @Primary
        UnitPointerStore inMemoryPointers() {
            return new UnitPointerStore() {
                @Override
                public void move(String unitKey, String versionId, Instant now) {
                    InMemoryContent.this.pointers.put(
                            unitKey, new UnitPointer(unitKey, versionId, now));
                }

                @Override
                public Optional<UnitPointer> findById(String unitKey) {
                    return Optional.ofNullable(InMemoryContent.this.pointers.get(unitKey));
                }

                @Override
                public List<UnitPointer> findAll() {
                    return List.copyOf(InMemoryContent.this.pointers.values());
                }
            };
        }
    }
}
