package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.account.infrastructure.AccountIndexes;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Confirmação de e-mail contra o Mongo 8 (história 2.2): o que o cadastro grava em {@code
 * one_time_tokens} e {@code mail_outbox}, os índices (único e TTL) dos tokens, a confirmação pela
 * rota, o reuso, o vencimento, o reenvio e o consumo atômico sob concorrência.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class EmailConfirmationIT {

    private static final Pattern TOKEN =
            Pattern.compile("confirmar-email\\?token=([A-Za-z0-9_-]{43})");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private AccountIndexes indexes;

    @BeforeEach
    void clean() {
        for (String collection :
                List.of("users", "sessions", "events", "one_time_tokens", "mail_outbox")) {
            this.mongo.remove(new Query(), collection);
        }
    }

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

    private ResultActions confirm(String token) throws Exception {
        return this.mvc.perform(
                post("/api/v1/account/email-confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("token", token).toString()));
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

    private String lastToken() {
        List<Document> mails = all("mail_outbox");
        Matcher matcher = TOKEN.matcher(mails.getLast().getString("text"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void aSignupStoresTheTokenHashAndOnePendingMail() throws Exception {
        signup("ana@example.com");
        String token = lastToken();

        List<Document> tokens = all("one_time_tokens");
        assertThat(tokens).hasSize(1);
        Document stored = tokens.getFirst();
        assertThat(stored.keySet())
                .containsExactlyInAnyOrder(
                        "_id", "tokenHash", "userId", "purpose", "email", "createdAt", "expiresAt");
        assertThat(stored.getString("tokenHash")).isEqualTo(OpaqueTokens.hash(token));
        assertThat(stored.toJson()).doesNotContain(token);
        assertThat(stored.getObjectId("userId"))
                .isEqualTo(all("users").getFirst().getObjectId("_id"));
        assertThat(stored.getString("purpose")).isEqualTo("EMAIL_CONFIRMATION");
        assertThat(
                        Duration.between(
                                stored.getDate("createdAt").toInstant(),
                                stored.getDate("expiresAt").toInstant()))
                .isEqualTo(Duration.ofDays(7));

        List<Document> mails = all("mail_outbox");
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().getString("status")).isEqualTo("PENDING");
        assertThat(mails.getFirst().getString("priority")).isEqualTo("NORMAL");
        assertThat(mails.getFirst().getString("to")).isEqualTo("ana@example.com");

        // O evento continua sem e-mail nem token.
        assertThat(all("events").getFirst().toJson())
                .doesNotContain("ana@example.com")
                .doesNotContain(token);
    }

    @Test
    void theTokenIndexesAreUniqueAndTtl() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection("one_time_tokens").listIndexes().into(indexes);
        Document unique =
                indexes.stream()
                        .filter(i -> "tokenHash_unique".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice único ausente"));
        assertThat(unique.getBoolean("unique")).isTrue();
        Document ttl =
                indexes.stream()
                        .filter(i -> "expiresAt_ttl".equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice TTL ausente"));
        assertThat(ttl.get("key", Document.class).keySet()).containsExactly("expiresAt");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isZero();
    }

    @Test
    void theLinkConfirmsOnceAndTheProfileSaysSo() throws Exception {
        String bearer = signup("ana@example.com");
        String token = lastToken();
        confirm(token).andExpect(status().isOk());

        assertThat(all("users").getFirst().getDate("emailVerifiedAt")).isNotNull();
        assertThat(all("one_time_tokens")).isEmpty();
        this.mvc
                .perform(get("/api/v1/students/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.emailConfirmed").value(true));

        confirm(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_LINK_INVALID"));
    }

    @Test
    void anExpiredTokenIsGoneEvenBeforeTheTtlRemovesIt() throws Exception {
        signup("ana@example.com");
        String token = lastToken();
        this.mongo
                .getCollection("one_time_tokens")
                .updateMany(
                        new Document(),
                        new Document(
                                "$set",
                                new Document(
                                        "expiresAt", new Date(System.currentTimeMillis() - 1000))));
        confirm(token).andExpect(status().isGone());
        assertThat(all("users").getFirst().get("emailVerifiedAt")).isNull();
    }

    @Test
    void aTokenForAnEmailTheAccountNoLongerHasIsGone() throws Exception {
        signup("ana@example.com");
        String token = lastToken();
        // A troca de e-mail é da 2.9; aqui, o e-mail da conta muda direto no banco.
        this.mongo
                .getCollection("users")
                .updateMany(
                        new Document(),
                        new Document("$set", new Document("email", "nova@example.com")));
        confirm(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_LINK_INVALID"));
        assertThat(all("users").getFirst().get("emailVerifiedAt")).isNull();
        assertThat(all("one_time_tokens")).isEmpty();
    }

    @Test
    void aResendReplacesTheTokenAndEnqueuesAnotherMail() throws Exception {
        String bearer = signup("ana@example.com");
        String first = lastToken();
        this.mvc
                .perform(
                        post("/api/v1/students/me/email-confirmation")
                                .header("Authorization", bearer))
                .andExpect(status().isAccepted());
        assertThat(all("mail_outbox")).hasSize(2);
        assertThat(all("one_time_tokens")).hasSize(1);
        confirm(first).andExpect(status().isGone());
        confirm(lastToken()).andExpect(status().isOk());
    }

    @Test
    void simultaneousConfirmationsOfTheSameTokenYieldOneOk() throws Exception {
        signup("ana@example.com");
        String token = lastToken();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return confirm(token).andReturn().getResponse().getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses).filteredOn(s -> s == 410).hasSize(threads - 1);
    }
}
