package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.auth.infrastructure.AuthIndexes;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperação de senha contra o Mongo 8 (história 2.8): o token {@code PASSWORD_RESET} só como
 * hash, o e-mail {@code HIGH} no outbox, a gravação condicional da senha, todas as sessões da conta
 * apagadas (e as de outra conta não), o índice {@code userId} de {@code sessions}, a faixa do
 * e-mail não confirmado e o consumo atômico sob concorrência. O executor da recuperação é síncrono.
 *
 * <p>Os limites por e-mail (1 por minuto) ficam em memória no bean e sobrevivem entre os testes do
 * mesmo contexto: cada teste que pede o link usa um e-mail próprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocalMongoContainer.class, PasswordRecoveryIT.SynchronousExecutor.class})
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class PasswordRecoveryIT {

    private static final Pattern TOKEN =
            Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]{43})");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private AuthIndexes indexes;

    @TestConfiguration(proxyBeanMethods = false)
    static class SynchronousExecutor {

        @Bean
        @Primary
        PasswordRecoveryExecutor synchronousRecoveryExecutor() {
            return new PasswordRecoveryExecutor(Runnable::run);
        }
    }

    @BeforeEach
    void clean() {
        for (String collection :
                List.of(
                        "users",
                        "sessions",
                        "events",
                        "one_time_tokens",
                        "mail_outbox",
                        "login_throttle")) {
            this.mongo.remove(new Query(), collection);
        }
    }

    /** Cadastra pelo Android e devolve o Bearer. */
    private String signup(String email) throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", email)
                        .put("password", "senha123")
                        .put("birthDate", "1995-05-20")
                        .put("name", "Ana")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        String response =
                this.mvc
                        .perform(
                                post("/api/v1/auth/signup")
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private String login(String email, String password) throws Exception {
        String response =
                this.mvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                MAPPER.createObjectNode()
                                                        .put("email", email)
                                                        .put("password", password)
                                                        .toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private void confirmAll() {
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("emailVerifiedAt", new Date())));
    }

    private ResultActions recover(String email) throws Exception {
        return this.mvc.perform(
                post("/api/v1/auth/password-recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("email", email).toString()));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return this.mvc.perform(
                post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("token", token)
                                        .put("password", password)
                                        .toString()));
    }

    private List<Document> all(String collection) {
        List<Document> documents = new ArrayList<>();
        this.mongo
                .getCollection(collection)
                .find()
                .sort(new Document("createdAt", 1))
                .into(documents);
        return documents;
    }

    private Document user(String email) {
        return this.mongo.getCollection("users").find(new Document("email", email)).first();
    }

    private String lastResetToken() {
        List<Document> mails = all("mail_outbox");
        Matcher matcher = TOKEN.matcher(mails.getLast().getString("text"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void aResetStoresOnlyTheHashChangesThePasswordAndEndsOnlyThatAccountsSessions()
            throws Exception {
        String first = signup("ana@example.com");
        String bia = signup("bia@example.com");
        confirmAll();
        String second = login("ana@example.com", "senha123");
        this.mongo.remove(new Query(), "one_time_tokens");

        recover("ANA@example.com").andExpect(status().isAccepted());
        String token = lastResetToken();

        List<Document> tokens = all("one_time_tokens");
        assertThat(tokens).hasSize(1);
        Document stored = tokens.getFirst();
        assertThat(stored.getString("purpose")).isEqualTo("PASSWORD_RESET");
        assertThat(stored.getString("tokenHash")).isEqualTo(OpaqueTokens.hash(token));
        assertThat(stored.toJson()).doesNotContain(token);
        assertThat(stored.getObjectId("userId"))
                .isEqualTo(user("ana@example.com").getObjectId("_id"));
        assertThat(
                        Duration.between(
                                stored.getDate("createdAt").toInstant(),
                                stored.getDate("expiresAt").toInstant()))
                .isEqualTo(Duration.ofHours(1));
        Document mail = all("mail_outbox").getLast();
        assertThat(mail.getString("priority")).isEqualTo("HIGH");
        assertThat(mail.getString("to")).isEqualTo("ana@example.com");

        reset(token, "novaSenha9").andExpect(status().isNoContent());

        assertThat(all("one_time_tokens")).isEmpty();
        assertThat(
                        new BCryptPasswordEncoder()
                                .matches(
                                        "novaSenha9",
                                        user("ana@example.com").getString("passwordHash")))
                .isTrue();
        // As duas sessões da Ana sumiram; a da Bia ficou.
        ObjectIdCount count = new ObjectIdCount(this.mongo);
        assertThat(count.sessionsOf(user("ana@example.com"))).isZero();
        assertThat(count.sessionsOf(user("bia@example.com"))).isEqualTo(1);
        for (String bearer : List.of(first, second)) {
            this.mvc
                    .perform(get("/api/v1/students/me").header("Authorization", bearer))
                    .andExpect(status().isUnauthorized());
        }
        this.mvc
                .perform(get("/api/v1/students/me").header("Authorization", bia))
                .andExpect(status().isOk());
        login("ana@example.com", "novaSenha9");

        reset(token, "outraSenha7")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_LINK_INVALID"));
    }

    @Test
    void anUnconfirmedAccountGetsNothing() throws Exception {
        signup("ana@example.com");
        this.mongo.remove(new Query(), "mail_outbox");
        this.mongo.remove(new Query(), "one_time_tokens");
        recover("ana@example.com").andExpect(status().isAccepted());
        assertThat(all("one_time_tokens")).isEmpty();
        assertThat(all("mail_outbox")).isEmpty();
    }

    @Test
    void aDeactivatedAccountSpendsTheTokenAndKeepsThePassword() throws Exception {
        signup("bia@example.com");
        confirmAll();
        recover("bia@example.com").andExpect(status().isAccepted());
        String token = lastResetToken();
        String hash = user("bia@example.com").getString("passwordHash");
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("status", "DEACTIVATED")));

        reset(token, "novaSenha9")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_LINK_INVALID"));
        assertThat(user("bia@example.com").getString("passwordHash")).isEqualTo(hash);
        assertThat(
                        all("one_time_tokens").stream()
                                .filter(t -> "PASSWORD_RESET".equals(t.getString("purpose"))))
                .isEmpty();
    }

    @Test
    void anEmailChangedAfterTheRequestSpendsTheTokenAndKeepsThePassword() throws Exception {
        signup("dani@example.com");
        confirmAll();
        recover("dani@example.com").andExpect(status().isAccepted());
        String token = lastResetToken();
        String hash = user("dani@example.com").getString("passwordHash");
        // A troca de e-mail é da 2.9; aqui, o e-mail da conta muda direto no banco.
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("email", "dani.nova@example.com")));

        reset(token, "novaSenha9")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_LINK_INVALID"));
        assertThat(user("dani.nova@example.com").getString("passwordHash")).isEqualTo(hash);
    }

    @Test
    void anUnconfirmedEmailAfterTheRequestSpendsTheTokenAndKeepsThePassword() throws Exception {
        signup("edu@example.com");
        confirmAll();
        recover("edu@example.com").andExpect(status().isAccepted());
        String token = lastResetToken();
        String hash = user("edu@example.com").getString("passwordHash");
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$unset", new Document("emailVerifiedAt", "")));

        reset(token, "novaSenha9")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_LINK_INVALID"));
        assertThat(user("edu@example.com").getString("passwordHash")).isEqualTo(hash);
    }

    @Test
    void theOverdueBannerComesFromTheStoredCreatedAt() throws Exception {
        String bearer = signup("ana@example.com");
        this.mvc
                .perform(get("/api/v1/students/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.emailConfirmationOverdue").value(false));
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document(
                                "$set",
                                new Document(
                                        "createdAt",
                                        new Date(
                                                System.currentTimeMillis()
                                                        - Duration.ofDays(8).toMillis()))));
        this.mvc
                .perform(get("/api/v1/students/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.emailConfirmationOverdue").value(true));
    }

    @Test
    void theSessionsUserIdIndexExists() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("sessions").listIndexes().into(indexes);
        Document userId =
                indexes.stream()
                        .filter(i -> "userId".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice userId ausente"));
        assertThat(userId.get("key", Document.class).keySet()).containsExactly("userId");
    }

    @Test
    void simultaneousResetsOfTheSameTokenYieldOneNoContent() throws Exception {
        signup("caio@example.com");
        confirmAll();
        recover("caio@example.com").andExpect(status().isAccepted());
        String token = lastResetToken();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String password = "novaSenha" + i;
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return reset(token, password).andReturn().getResponse().getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsOnlyOnce(204);
        assertThat(statuses).filteredOn(s -> s == 410).hasSize(threads - 1);
    }

    /** Conta as sessões de uma conta pelo {@code userId} ({@code ObjectId}). */
    private record ObjectIdCount(MongoTemplate mongo) {

        long sessionsOf(Document user) {
            return this.mongo
                    .getCollection("sessions")
                    .countDocuments(new Document("userId", user.getObjectId("_id")));
        }
    }
}
