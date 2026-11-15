package com.fluveny.fluveny_backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.content.infrastructure.ContentIndexes;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.platform.DeployTokenTest;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Publicação e rotas do aluno contra o Mongo 8 (história 1.7): a matriz de I/O da spec inteira e a
 * varredura de segredos de todas as respostas das rotas públicas de {@code content}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocalMongoContainer.class, ContentPublicationIT.Types.class})
class ContentPublicationIT {

    static final String TOKEN = UUID.randomUUID().toString();

    static final String UNIT = "/api/v1/admin/content/units/unit-1/versions";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @DynamicPropertySource
    static void tokenHash(DynamicPropertyRegistry registry) {
        registry.add("FLUVENY_DEPLOY_TOKEN_SHA256", () -> DeployTokenTest.sha256Hex(TOKEN));
    }

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private ContentIndexes indexes;

    @BeforeEach
    void cleanDatabase() {
        this.mongo.dropCollection("unit_versions");
        this.mongo.dropCollection("unit_pointers");
        this.indexes.ensure();
        Types.SINCE.set("0.0.0");
    }

    static ObjectNode validBundle() throws Exception {
        Path root =
                Path.of(ContentPublicationIT.class.getResource("/content-fixtures/valid").toURI());
        return UnitBundle.fromDirectory(root.resolve("units/unit-1"), root.resolve("media.json"));
    }

    static ObjectNode comingSoon(String unitKey, int order) {
        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.putObject("unit")
                .put("unitKey", unitKey)
                .put("order", order)
                .put("schemaVersion", 1)
                .put("title", "Coming soon")
                .<ObjectNode>set("stages", MAPPER.createArrayNode())
                .set("usefulExpressions", MAPPER.createArrayNode());
        bundle.putArray("stages");
        bundle.putObject("media");
        return bundle;
    }

    private ResultActions publish(String path, JsonNode bundle) throws Exception {
        return this.mvc.perform(authorized(post(path), bundle));
    }

    private static MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request, JsonNode bundle) {
        return request.header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bundle.toString());
    }

    private long versions() {
        return this.mongo.getCollection("unit_versions").countDocuments();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return MAPPER.readTree(
                result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // --- publicação ---

    @Test
    void publishingNewContentCreatesTheVersionAndMovesThePointer() throws Exception {
        String id =
                json(publish(UNIT, validBundle())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.created").value(true))
                                .andExpect(jsonPath("$.data.published").value(true)))
                        .at("/data/versionId")
                        .asString();
        assertThat(id).matches("sha256:[0-9a-f]{64}");

        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data[0].versionId").value(id))
                .andExpect(jsonPath("$.data[0].comingSoon").value(false));

        var stored = this.mongo.findById(id, org.bson.Document.class, "unit_versions");
        assertThat(stored).isNotNull();
        assertThat(stored.getString("unitKey")).isEqualTo("unit-1");
        assertThat(stored.getInteger("order")).isEqualTo(1);
        assertThat(stored.getInteger("schemaVersion")).isEqualTo(1);
        assertThat(stored.get("createdAt")).isNotNull();
        // O hash é sempre recalculável a partir do JSON canônico guardado.
        assertThat(
                        com.fluveny.fluveny_backend.content.publication.CanonicalJson.sha256Id(
                                stored.getString("canonicalJson")))
                .isEqualTo(id);
    }

    @Test
    void republishingTheSameContentCreatesNothing() throws Exception {
        publish(UNIT, validBundle()).andExpect(status().isCreated());
        ObjectNode reordered = validBundle();
        // Mesma semântica, outra ordem de chaves: mesmo JSON canônico.
        JsonNode unit = reordered.remove("unit");
        reordered.set("unit", unit);
        publish(UNIT, reordered)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.published").value(true));
        assertThat(versions()).isEqualTo(1);
    }

    @Test
    void aCandidateIsStoredWithoutMovingThePointer() throws Exception {
        String published = json(publish(UNIT, validBundle())).at("/data/versionId").asString();
        ObjectNode candidate = validBundle();
        ((ObjectNode) candidate.get("unit")).put("title", "Unit 1: candidate");

        String candidateId =
                json(publish(UNIT + "?publish=false", candidate)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.created").value(true))
                                .andExpect(jsonPath("$.data.published").value(false)))
                        .at("/data/versionId")
                        .asString();

        assertThat(candidateId).isNotEqualTo(published);
        assertThat(versions()).isEqualTo(2);
        this.mvc
                .perform(get("/api/v1/units/unit-1"))
                .andExpect(jsonPath("$.data.versionId").value(published));
        this.mvc
                .perform(get(UNIT + "/" + candidateId).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(false));
    }

    @Test
    void invalidContentIs422WithTheErrorsAndNothingIsStored() throws Exception {
        ObjectNode bundle = validBundle();
        ((ObjectNode) bundle.at("/stages/0/activities/0/items/0")).remove("correctOptionKey");

        publish(UNIT, bundle)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT"))
                .andExpect(
                        jsonPath("$.errors[0].file").value("units/unit-1/stages/s1-meet-alex.json"))
                .andExpect(
                        jsonPath("$.errors[0].pointer")
                                .value("/activities/0/items/0/correctOptionKey"))
                .andExpect(jsonPath("$.errors[0].key").value("s1-greetings-hello"))
                .andExpect(jsonPath("$.errors[0].rule").value("MISSING_ACCEPTED_ANSWER"));
        assertThat(versions()).isZero();
    }

    @Test
    void unsupportedTypesAre422AndNothingIsStored() throws Exception {
        for (String since : new String[] {null, "9.9.9"}) {
            Types.SINCE.set(since);
            publish(UNIT, validBundle())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_ACTIVITY_TYPE"))
                    .andExpect(jsonPath("$.errors[0].rule").value("UNSUPPORTED_ACTIVITY_TYPE"))
                    .andExpect(jsonPath("$.errors[0].pointer").value("/activities/0/type"))
                    .andExpect(jsonPath("$.errors[0].key").value("s1-greetings"));
        }
        assertThat(versions()).isZero();
    }

    @Test
    void theUnitKeyOfThePathMustMatchTheBundle() throws Exception {
        publish("/api/v1/admin/content/units/unit-9/versions", validBundle())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNIT_KEY_MISMATCH"));
        publish("/api/v1/admin/content/units/Unit_9/versions", validBundle())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
        assertThat(versions()).isZero();
    }

    @Test
    void withoutTokenIs401AndAWrongTokenIs403() throws Exception {
        this.mvc
                .perform(
                        post(UNIT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBundle().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        this.mvc
                .perform(
                        post(UNIT)
                                .header("Authorization", "Bearer " + TOKEN + "0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBundle().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        this.mvc
                .perform(get(UNIT + "/sha256:" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());
        assertThat(versions()).isZero();
    }

    @Test
    void versionMetadataIs200Or404() throws Exception {
        String id = json(publish(UNIT, validBundle())).at("/data/versionId").asString();
        this.mvc
                .perform(get(UNIT + "/" + id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(id))
                .andExpect(jsonPath("$.data.unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.canonicalJson").doesNotExist());
        this.mvc
                .perform(
                        get(UNIT + "/sha256:" + "0".repeat(64))
                                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIT_VERSION_NOT_FOUND"));
        this.mvc
                .perform(
                        get("/api/v1/admin/content/units/unit-2/versions/" + id)
                                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
        this.mvc
                .perform(get(UNIT + "/md5:abc").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
    }

    // --- rotas do aluno ---

    @Test
    void comingSoonUnitIsListedAfterTheFirstByOrder() throws Exception {
        publish("/api/v1/admin/content/units/unit-2/versions", comingSoon("unit-2", 2))
                .andExpect(status().isCreated());
        publish(UNIT, validBundle()).andExpect(status().isCreated());

        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data[0].order").value(1))
                .andExpect(jsonPath("$.data[1].unitKey").value("unit-2"))
                .andExpect(jsonPath("$.data[1].comingSoon").value(true));
        this.mvc
                .perform(get("/api/v1/units/unit-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comingSoon").value(true))
                .andExpect(jsonPath("$.data.stages").isEmpty());
    }

    @Test
    void unitDetailHasTheStagesAndUsefulExpressions() throws Exception {
        publish(UNIT, validBundle());
        this.mvc
                .perform(get("/api/v1/units/unit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Unit 1: Hello!"))
                .andExpect(jsonPath("$.data.stages[0].stageKey").value("s1-meet-alex"))
                .andExpect(jsonPath("$.data.stages[0].title").value("Meet Alex"))
                .andExpect(jsonPath("$.data.stages[0].activityCount").value(1))
                .andExpect(jsonPath("$.data.stages[1].stageKey").value("s2-where-from"))
                .andExpect(
                        jsonPath("$.data.usefulExpressions[0].expressionKey")
                                .value("nice-to-meet-you"))
                .andExpect(jsonPath("$.data.usefulExpressions[1].examples[0]").exists());
    }

    @Test
    void onlyTheFirstStageIsOpenToVisitors() throws Exception {
        String id = json(publish(UNIT, validBundle())).at("/data/versionId").asString();
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(id))
                .andExpect(jsonPath("$.data.activities[0].type").value("choice"))
                .andExpect(
                        jsonPath("$.data.activities[0].items[0].correctOptionKey").doesNotExist())
                .andExpect(jsonPath("$.data.activities[0].items[0].justification").doesNotExist());
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s2-where-from"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void theFirstStageOfAnotherUnitIsLocked() throws Exception {
        publish(UNIT, validBundle());
        ObjectNode second = validBundle();
        ((ObjectNode) second.get("unit")).put("unitKey", "unit-2").put("order", 2);
        // Chaves únicas na Unidade (AD-5); outra Unidade pode repetir, mas não a mesma.
        publish("/api/v1/admin/content/units/unit-2/versions", second)
                .andExpect(status().isCreated());
        this.mvc
                .perform(get("/api/v1/units/unit-2/stages/s1-meet-alex"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unpublishedUnitsAreHidden() throws Exception {
        publish(
                        "/api/v1/admin/content/units/unit-2/versions?publish=false",
                        comingSoon("unit-2", 2))
                .andExpect(status().isCreated());
        this.mvc.perform(get("/api/v1/units")).andExpect(jsonPath("$.data").isEmpty());
        this.mvc
                .perform(get("/api/v1/units/unit-2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIT_NOT_FOUND"));
        this.mvc.perform(get("/api/v1/units/unit-2/stages/s1")).andExpect(status().isNotFound());
    }

    @Test
    void invalidAndUnknownKeys() throws Exception {
        publish(UNIT, validBundle());
        this.mvc
                .perform(get("/api/v1/units/Unit_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/S1!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s9-none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_NOT_FOUND"));
    }

    /**
     * Uma Unidade com todas as Atividades da fixture (cheias de segredo) no Estágio liberado:
     * nenhuma resposta das rotas públicas de {@code content} leva uma propriedade {@code
     * x-fluveny-secret}.
     */
    @Test
    void noPublicContentResponseHasASecretProperty() throws Exception {
        ObjectNode bundle = validBundle();
        ArrayNode stages = (ArrayNode) bundle.get("stages");
        ArrayNode first = (ArrayNode) stages.get(0).get("activities");
        for (int i = 1; i < stages.size(); i++) {
            stages.get(i).get("activities").forEach(first::add);
        }
        while (stages.size() > 1) {
            stages.remove(1);
        }
        ((ObjectNode) bundle.get("unit")).putArray("stages").add("s1-meet-alex");
        publish(UNIT, bundle).andExpect(status().isCreated());

        Set<String> secrets = StudentRoutesOpenApiTest.secretNames();
        assertThat(secrets).isNotEmpty();
        Set<String> scanned = new TreeSet<>();
        for (String path :
                List.of(
                        "/api/v1/units",
                        "/api/v1/units/unit-1",
                        "/api/v1/units/unit-1/stages/s1-meet-alex")) {
            JsonNode body = json(this.mvc.perform(get(path)).andExpect(status().isOk()));
            scanned.addAll(keys(body, new TreeSet<>()));
            assertThat(keys(body, new TreeSet<>())).as(path).doesNotContainAnyElementsOf(secrets);
        }
        assertThat(scanned).contains("activities", "items", "options", "mediaKey");
        JsonNode stage = json(this.mvc.perform(get("/api/v1/units/unit-1/stages/s1-meet-alex")));
        assertThat(stage.at("/data/activities").size()).isEqualTo(2);
    }

    static Set<String> keys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                keys.add(property.getKey());
                keys(property.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(element -> keys(element, keys));
        }
        return keys;
    }

    /** O Tipo {@code choice} com o {@code sinceAppVersion} que cada teste quiser. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Types {

        static final AtomicReference<String> SINCE = new AtomicReference<>("0.0.0");

        @Bean
        ActivityTypeSource testChoiceType() {
            return () -> List.of(new ActivityTypeSupport("choice", SINCE.get()));
        }
    }
}
