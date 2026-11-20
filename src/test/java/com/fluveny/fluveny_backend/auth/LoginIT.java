package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.auth.infrastructure.AuthIndexes;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrar e sair contra o Mongo 8 (história 2.3): o que fica em {@code login_throttle} (só hashes,
 * sem e-mail nem IP), os índices dele (único e TTL de 1 h depois da última falha), o sucesso que
 * zera as chaves da conta e o logout que apaga a sessão em {@code sessions}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class LoginIT {

    private static final String ME = "/api/v1/students/me";

    private static final String EMAIL = "ana@example.com";

    private static final String IP = "203.0.113.77";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private AuthIndexes indexes;

    @BeforeEach
    void cleanCollections() {
        for (String collection : List.of("users", "sessions", "events", "login_throttle")) {
            this.mongo.remove(new Query(), collection);
        }
    }

    private void signup() throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", EMAIL)
                        .put("password", "senha123")
                        .put("birthDate", "1995-05-20")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        this.mvc
                .perform(
                        post("/api/v1/auth/signup")
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated());
        this.mongo.remove(new Query(), "sessions");
    }

    private ResultActions login(String password, String client) throws Exception {
        return this.mvc.perform(
                post("/api/v1/auth/login")
                        .header("X-Fluveny-Client", client)
                        .header("X-Fluveny-Client-IP", IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("email", "Ana@Example.com")
                                        .put("password", password)
                                        .toString()));
    }

    private List<Document> throttle() {
        List<Document> documents = new ArrayList<>();
        this.mongo.getCollection("login_throttle").find().into(documents);
        return documents;
    }

    @Test
    void failuresStoreOnlyHashesAndASuccessClearsTheAccountKeys() throws Exception {
        signup();
        for (int i = 0; i < 2; i++) {
            login("errada123", "web")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        List<Document> documents = throttle();
        assertThat(documents).hasSize(3);
        for (Document document : documents) {
            assertThat(document.keySet())
                    .containsExactlyInAnyOrder(
                            "_id", "keyHash", "kind", "failures", "lastFailureAt", "expiresAt");
            assertThat(document.getString("keyHash")).matches("[0-9a-f]{64}");
            assertThat(document.toJson())
                    .doesNotContainIgnoringCase("ana")
                    .doesNotContain("example")
                    .doesNotContain(IP);
            assertThat(document.getInteger("failures")).isEqualTo(2);
            assertThat(
                            Duration.between(
                                    document.getDate("lastFailureAt").toInstant(),
                                    document.getDate("expiresAt").toInstant()))
                    .isEqualTo(Duration.ofHours(1));
        }
        assertThat(documents)
                .extracting(document -> document.getString("kind"))
                .containsExactlyInAnyOrder("ip", "account_ip", "account");
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isZero();

        login("senha123", "web").andExpect(status().isOk());
        assertThat(throttle())
                .singleElement()
                .satisfies(document -> assertThat(document.getString("kind")).isEqualTo("ip"));
    }

    @Test
    void theThrottleIndexesAreUniqueAndTtl() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("login_throttle").listIndexes().into(indexes);
        Document unique =
                indexes.stream()
                        .filter(i -> "keyHash_unique".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice único ausente"));
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("key", Document.class).keySet()).containsExactly("keyHash");
        Document ttl =
                indexes.stream()
                        .filter(i -> "expiresAt_ttl".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice TTL ausente"));
        assertThat(ttl.get("key", Document.class).keySet()).containsExactly("expiresAt");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isZero();
    }

    @Test
    void theFourthAttemptAfterThreeFailuresIs429AgainstMongo() throws Exception {
        signup();
        for (int i = 0; i < 3; i++) {
            login("errada123", "web").andExpect(status().isUnauthorized());
        }
        login("senha123", "web")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isZero();
        assertThat(throttle())
                .allSatisfy(document -> assertThat(document.getInteger("failures")).isEqualTo(3));
    }

    @Test
    void anExpiredDocumentStartsOverAtOne() throws Exception {
        signup();
        for (int i = 0; i < 3; i++) {
            login("errada123", "web").andExpect(status().isUnauthorized());
        }
        // Vencido, mas o TTL do Mongo (a cada minuto) ainda não apagou.
        this.mongo
                .getCollection("login_throttle")
                .updateMany(
                        new Document(),
                        new Document(
                                "$set",
                                new Document(
                                        "expiresAt",
                                        java.util.Date.from(
                                                java.time.Instant.now().minusSeconds(1)))));
        login("errada123", "web")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(throttle())
                .hasSize(3)
                .allSatisfy(document -> assertThat(document.getInteger("failures")).isEqualTo(1));
    }

    /** 1º critério de aceite: entra, sai, e a mesma credencial dá 401. */
    @Test
    void afterTheLogoutTheSameCredentialIs401() throws Exception {
        signup();
        String setCookie =
                login("senha123", "web")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        Cookie cookie =
                new Cookie(
                        "fluveny_session",
                        setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';')));
        String body =
                login("senha123", "android")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String bearer =
                "Bearer " + MAPPER.readTree(body).path("data").path("sessionToken").asString();
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isEqualTo(2);
        this.mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk());

        this.mvc
                .perform(
                        post("/api/v1/auth/logout")
                                .cookie(cookie)
                                .header("X-Fluveny-Client", "web"))
                .andExpect(status().isNoContent())
                .andExpect(
                        header().string(
                                        HttpHeaders.SET_COOKIE,
                                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        this.mvc.perform(get(ME).cookie(cookie)).andExpect(status().isUnauthorized());
        this.mvc.perform(get(ME).header("Authorization", bearer)).andExpect(status().isOk());

        this.mvc
                .perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", bearer)
                                .header("X-Fluveny-Client", "android"))
                .andExpect(status().isNoContent());
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isZero();
    }

    @Test
    void aDeactivatedAccountWithTheRightPasswordIs403() throws Exception {
        signup();
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("status", "DEACTIVATED")));
        login("senha123", "web")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DEACTIVATED"));
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isZero();
    }
}
