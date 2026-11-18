package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.auth.infrastructure.AuthIndexes;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Cadastro e sessão contra o Mongo 8 (história 2.1): o que fica em {@code users}, {@code sessions}
 * e {@code events}, os índices de {@code sessions} (único e TTL), o vencimento, a conta inativa, a
 * renovação deslizante e a corrida de dois cadastros do mesmo e-mail pela rota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class SignupIT {

    private static final String SIGNUP = "/api/v1/auth/signup";

    private static final String ME = "/api/v1/students/me";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private AuthIndexes indexes;

    @BeforeEach
    void cleanCollections() {
        for (String collection : List.of("users", "sessions", "events")) {
            this.mongo.remove(new Query(), collection);
        }
    }

    private ResultActions signup(String email, String client) throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", email)
                        .put("password", "senha123")
                        .put("birthDate", "1995-05-20")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        return this.mvc.perform(
                post(SIGNUP)
                        .header("X-Fluveny-Client", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private String webToken(String email) throws Exception {
        String setCookie =
                signup(email, "web")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
    }

    private String androidToken(String email) throws Exception {
        String response =
                signup(email, "android")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private Document only(String collection) {
        assertThat(this.mongo.getCollection(collection).countDocuments()).isEqualTo(1);
        return this.mongo.getCollection(collection).find().first();
    }

    private void setOnSession(String field, Object value) {
        this.mongo
                .getCollection("sessions")
                .updateMany(new Document(), new Document("$set", new Document(field, value)));
    }

    @Test
    void aWebSignupStoresTheAccountTheSessionAndOneEventWithOnlyTheIdentifier() throws Exception {
        String token = webToken("Ana@Example.com");

        Document user = only("users");
        assertThat(user.getString("email")).isEqualTo("ana@example.com");

        Document session = only("sessions");
        assertThat(session.keySet())
                .containsExactlyInAnyOrder(
                        "_id", "tokenHash", "userId", "role", "createdAt", "expiresAt");
        assertThat(session.getString("tokenHash"))
                .isEqualTo(Sessions.hash(token))
                .doesNotContain(token);
        assertThat(session.getObjectId("userId")).isEqualTo(user.getObjectId("_id"));
        assertThat(session.getString("role")).isEqualTo("STUDENT");
        assertThat(
                        Duration.between(
                                session.getDate("createdAt").toInstant(),
                                session.getDate("expiresAt").toInstant()))
                .isEqualTo(Duration.ofDays(30));

        // 2º critério de aceite: o evento tem só type, occurredAt, metricsId e fromVisitor.
        Document event = only("events");
        assertThat(event.keySet())
                .containsExactlyInAnyOrder("_id", "type", "occurredAt", "metricsId", "fromVisitor");
        assertThat(event.getString("type")).isEqualTo("STUDENT_REGISTERED");
        assertThat(event.getString("metricsId")).isEqualTo(user.getString("metricsId"));
        assertThat(event.getBoolean("fromVisitor")).isFalse();

        this.mvc
                .perform(get(ME).cookie(new Cookie("fluveny_session", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ana@example.com"));
    }

    @Test
    void anAndroidSessionReadsAndWritesTheProfile() throws Exception {
        String bearer = "Bearer " + androidToken("ana@example.com");
        this.mvc
                .perform(
                        patch(ME)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"HE\"}"))
                .andExpect(status().isOk());
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pronounSet").value("HE"));
        assertThat(only("users").getString("pronounSet")).isEqualTo("HE");
    }

    @Test
    void theSessionIndexesAreUniqueAndTtl() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("sessions").listIndexes().into(indexes);
        Document unique =
                indexes.stream()
                        .filter(i -> "tokenHash_unique".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice único ausente"));
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("key", Document.class).keySet()).containsExactly("tokenHash");
        Document ttl =
                indexes.stream()
                        .filter(i -> "expiresAt_ttl".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice TTL ausente"));
        assertThat(ttl.get("key", Document.class).keySet()).containsExactly("expiresAt");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isZero();
    }

    @Test
    void anExpiredSessionIs401() throws Exception {
        String bearer = "Bearer " + androidToken("ana@example.com");
        setOnSession("expiresAt", Date.from(Instant.now().minusSeconds(1)));
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void anInactiveAccountIs401() throws Exception {
        String bearer = "Bearer " + androidToken("ana@example.com");
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("status", "DEACTIVATED")));
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionRenewedMoreThanADayAgoSlidesAndTheCookieIsSentAgain() throws Exception {
        String token = webToken("ana@example.com");
        // A última renovação foi há dois dias: vence em 28.
        setOnSession("expiresAt", Date.from(Instant.now().plus(Duration.ofDays(28))));
        Instant before = Instant.now();
        String setCookie =
                this.mvc
                        .perform(get(ME).cookie(new Cookie("fluveny_session", token)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("fluveny_session=" + token + ";").contains("Max-Age=");
        Instant expiresAt = only("sessions").getDate("expiresAt").toInstant();
        assertThat(expiresAt)
                .isAfterOrEqualTo(before.plus(Duration.ofDays(30)).minusSeconds(1))
                .isBeforeOrEqualTo(Instant.now().plus(Duration.ofDays(30)));
    }

    @Test
    void twoSimultaneousSignupsOfTheSameEmailYieldOneCreatedAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return signup("ana@example.com", "web")
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(this.mongo.getCollection("users").countDocuments()).isEqualTo(1);
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isEqualTo(1);
        assertThat(this.mongo.getCollection("events").countDocuments()).isEqualTo(1);
    }
}
