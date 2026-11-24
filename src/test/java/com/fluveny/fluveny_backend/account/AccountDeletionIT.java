package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.student.ActivityResult;
import com.fluveny.fluveny_backend.learning.student.ActivityResultStore;
import com.fluveny.fluveny_backend.learning.student.UnitProgressStore;
import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * A exclusão da conta contra o Mongo (história 2.10, FR-6, NFR7): a matriz da rota e o teste
 * obrigatório de exclusão. Monta um Aluno com tudo o que dá para criar hoje (duas sessões, tokens
 * de confirmação e de troca de e-mail, {@code pendingEmail}, falhas de login, e-mails na fila,
 * Progresso e eventos), exclui e varre todas as coleções do banco: falha se uma coleção não está no
 * {@link AccountDeletionInventory} ou se sobra qualquer dado que leve à pessoa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocalMongoContainer.class, AccountDeletionIT.FailingHandler.class})
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class AccountDeletionIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String ME = "/api/v1/students/me";

    private static final String DELETION = "/api/v1/students/me/deletion";

    private static final String NAME = "Zuleika";

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private ActivityResultStore results;

    @Autowired private UnitProgressStore progress;

    @BeforeEach
    void clean() {
        for (String collection : this.mongo.getCollectionNames()) {
            if (!collection.startsWith("system.")) {
                this.mongo.remove(new Query(), collection);
            }
        }
        FailingHandler.FAIL.set(false);
    }

    @AfterEach
    void reset() {
        FailingHandler.FAIL.set(false);
    }

    /** Um tratador de teste que lança depois dos outros: simula a falha de um módulo. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingHandler {

        static final AtomicBoolean FAIL = new AtomicBoolean();

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    static class FailingListener {

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        void on(AccountDeletionRequested deletion) {
            if (FailingHandler.FAIL.get()) {
                throw new IllegalStateException("falha injetada no tratador");
            }
        }
    }

    private ResultActions signup(String email, String client) throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", email)
                        .put("password", "senha123")
                        .put("birthDate", "1995-05-20")
                        .put("name", email.startsWith("bia") ? "Bia" : NAME)
                        .put("country", "Brazil")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        return this.mvc.perform(
                post("/api/v1/auth/signup")
                        .header("X-Fluveny-Client", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private String signup(String email) throws Exception {
        return token(signup(email, "android").andExpect(status().isCreated()));
    }

    private ResultActions login(String email, String password) throws Exception {
        return this.mvc.perform(
                post("/api/v1/auth/login")
                        .header("X-Fluveny-Client", "android")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("email", email)
                                        .put("password", password)
                                        .toString()));
    }

    private static String token(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private ResultActions delete(String token, String body) throws Exception {
        return this.mvc.perform(
                post(DELETION)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions deleteWith(String token, String password) throws Exception {
        return delete(token, MAPPER.createObjectNode().put("currentPassword", password).toString());
    }

    private int sessionStatus(String token) throws Exception {
        return this.mvc
                .perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Document user(String email) {
        return this.mongo.getCollection("users").find(new Document("email", email)).first();
    }

    private List<Document> all(String collection) {
        List<Document> found = new ArrayList<>();
        this.mongo.getCollection(collection).find().into(found);
        return found;
    }

    private void progressFor(String userId) {
        this.results.insert(
                new ActivityResult(
                        userId,
                        "unit-1",
                        "s1-greetings",
                        "s1-meet-alex",
                        "sha256:" + "a".repeat(64),
                        "choice",
                        UUID.randomUUID(),
                        Outcome.GRADED,
                        80,
                        8,
                        1200,
                        false,
                        Instant.parse("2026-11-23T12:00:00Z")));
        this.progress.completeStage(
                userId,
                "unit-1",
                "s1-meet-alex",
                "sha256:" + "a".repeat(64),
                Instant.parse("2026-11-23T12:01:00Z"));
    }

    private static String throttleKey(String email) {
        return OpaqueTokens.hash("fluveny-login-throttle\naccount\n" + email);
    }

    /** O teste obrigatório: nada que leve à pessoa sobra, em nenhuma coleção. */
    @Test
    void theDeletionLeavesNoPersonalDataInAnyCollection() throws Exception {
        String email = "ana@example.com";
        String pendingEmail = "ana.nova@example.com";
        String first = signup(email);
        String second = token(login(email, "senha123").andExpect(status().isOk()));
        login(email, "errada99").andExpect(status().isUnauthorized());
        login(pendingEmail, "errada99").andExpect(status().isUnauthorized());
        this.mvc
                .perform(
                        post("/api/v1/students/me/email")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("newEmail", pendingEmail)
                                                .put("currentPassword", "senha123")
                                                .toString()))
                .andExpect(status().is2xxSuccessful());
        Document stored = user(email);
        String userId = stored.getObjectId("_id").toHexString();
        String metricsId = stored.getString("metricsId");
        assertThat(stored.getString("pendingEmail")).isEqualTo(pendingEmail);
        // Nada liga o deviceId do Visitante à conta (2.6).
        assertThat(stored.containsKey("deviceId")).isFalse();
        progressFor(userId);
        String other = signup("bia@example.com");
        String otherId = user("bia@example.com").getObjectId("_id").toHexString();
        progressFor(otherId);

        assertThat(all("sessions")).hasSize(3);
        assertThat(all("one_time_tokens")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all("login_throttle")).hasSizeGreaterThanOrEqualTo(5);
        assertThat(all("mail_outbox")).hasSizeGreaterThanOrEqualTo(3);
        long eventsBefore =
                all("events").stream()
                        .filter(event -> metricsId.equals(event.getString("metricsId")))
                        .count();
        assertThat(eventsBefore).isPositive();

        deleteWith(first, "senha123")
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        List<String> forbidden =
                List.of(
                        userId,
                        email,
                        pendingEmail,
                        NAME,
                        throttleKey(email),
                        throttleKey(pendingEmail),
                        OpaqueTokens.hash(first),
                        OpaqueTokens.hash(second));
        assertThat(
                        all("mail_outbox").stream()
                                .filter(mail ->
                                        AccountDeletion.SUBJECT.equals(mail.getString("subject")))
                                .map(mail -> mail.getString("to"))
                                .toList())
                .containsExactly(email);
        List<String> unknown = new ArrayList<>();
        for (String collection : this.mongo.getCollectionNames()) {
            if (collection.startsWith("system.")) {
                continue;
            }
            if (!AccountDeletionInventory.BY_COLLECTION.containsKey(collection)) {
                unknown.add(collection);
                continue;
            }
            for (Document document : all(collection)) {
                if (collection.equals("mail_outbox")
                        && AccountDeletion.SUBJECT.equals(document.getString("subject"))) {
                    // A confirmação da exclusão: só o endereço, sem nome nem link; some no envio.
                    assertThat(document.getString("to")).isEqualTo(email);
                    assertThat(document.getBoolean("purgeOnSend")).isTrue();
                    assertThat(document.getString("text")).doesNotContain(NAME, "http");
                    continue;
                }
                String json = document.toJson();
                for (String value : forbidden) {
                    assertThat(json).as("dado pessoal em %s", collection).doesNotContain(value);
                }
                if (!collection.equals("events")) {
                    assertThat(json)
                            .as("metricsId vinculado em %s", collection)
                            .doesNotContain(metricsId);
                }
            }
        }
        assertThat(unknown)
                .as("coleções fora do AccountDeletionInventory: inclua no inventário")
                .isEmpty();

        // Os eventos ficam, agregados e sem PII.
        List<Document> events =
                all("events").stream()
                        .filter(event -> metricsId.equals(event.getString("metricsId")))
                        .toList();
        assertThat(events).hasSize((int) eventsBefore);
        assertThat(events)
                .allSatisfy(event ->
                        assertThat(event.keySet())
                                .doesNotContain("userId", "email", "name", "deviceId"));
        // A chave ip do throttle fica; a outra conta segue intacta.
        assertThat(all("login_throttle"))
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.getString("kind")).isEqualTo("ip"));
        assertThat(sessionStatus(other)).isEqualTo(200);
        assertThat(this.results.findByUser(otherId)).hasSize(1);
        assertThat(this.progress.findByUser(otherId)).hasSize(1);
        assertThat(user("bia@example.com")).isNotNull();
        assertThat(
                        this.mongo
                                .getCollection("mail_outbox")
                                .countDocuments(new Document("to", "bia@example.com")))
                .isPositive();
    }

    @Test
    void afterTheDeletionSessionsLoginAndSignupBehaveAsForANewEmail() throws Exception {
        String email = "ana@example.com";
        String first = signup(email);
        String second = token(login(email, "senha123").andExpect(status().isOk()));
        deleteWith(first, "senha123").andExpect(status().isNoContent());

        assertThat(sessionStatus(first)).isEqualTo(401);
        assertThat(sessionStatus(second)).isEqualTo(401);
        login(email, "senha123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String again = signup(email);
        String newId = user(email).getObjectId("_id").toHexString();
        assertThat(sessionStatus(again)).isEqualTo(200);
        assertThat(this.results.findByUser(newId)).isEmpty();
        assertThat(this.progress.findByUser(newId)).isEmpty();
    }

    @Test
    void theWebDeletionExpiresTheCookie() throws Exception {
        String setCookie =
                signup("ana@example.com", "web")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        Cookie cookie =
                new Cookie(
                        "fluveny_session",
                        setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';')));
        this.mvc
                .perform(
                        post(DELETION)
                                .cookie(cookie)
                                .header("X-Fluveny-Client", "web")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"senha123\"}"))
                .andExpect(status().isNoContent())
                .andExpect(
                        header().string(
                                        HttpHeaders.SET_COOKIE,
                                        Matchers.containsString("Max-Age=0")));
        this.mvc.perform(get(ME).cookie(cookie)).andExpect(status().isUnauthorized());
        assertThat(user("ana@example.com")).isNull();
    }

    @Test
    void aWrongPasswordChangesNothingAndTheSixthErrorIs429() throws Exception {
        String token = signup("ana@example.com");
        for (int i = 0; i < 5; i++) {
            deleteWith(token, "errada99")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        }
        deleteWith(token, "senha123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertThat(user("ana@example.com").get("deletionRequestedAt")).isNull();
        assertThat(sessionStatus(token)).isEqualTo(200);
    }

    @Test
    void anInvalidBodyIs400() throws Exception {
        String token = signup("ana@example.com");
        delete(token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        delete(token, "{\"currentPassword\":\"senha123\",\"confirm\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(user("ana@example.com")).isNotNull();
        assertThat(sessionStatus(token)).isEqualTo(200);
    }

    @Test
    void aFailingHandlerRollsEverythingBack() throws Exception {
        String token = signup("ana@example.com");
        login("ana@example.com", "errada99").andExpect(status().isUnauthorized());
        String userId = user("ana@example.com").getObjectId("_id").toHexString();
        progressFor(userId);
        long mails = all("mail_outbox").size();
        long tokens = all("one_time_tokens").size();
        List<Document> throttle = all("login_throttle");
        assertThat(throttle).hasSize(3);
        FailingHandler.FAIL.set(true);

        deleteWith(token, "senha123")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_FAILED"));

        Document stored = user("ana@example.com");
        assertThat(stored).isNotNull();
        assertThat(stored.get("deletionRequestedAt")).isNull();
        assertThat(sessionStatus(token)).isEqualTo(200);
        assertThat(this.results.findByUser(userId)).hasSize(1);
        assertThat(this.progress.findByUser(userId)).hasSize(1);
        assertThat(all("mail_outbox")).hasSize((int) mails);
        assertThat(all("one_time_tokens")).hasSize((int) tokens);
        // Os tratadores de auth, mail e learning rodaram antes (HANDLER_ORDER) e foram desfeitos.
        assertThat(all("sessions")).hasSize(1);
        assertThat(all("login_throttle")).containsExactlyInAnyOrderElementsOf(throttle);

        FailingHandler.FAIL.set(false);
        deleteWith(token, "senha123").andExpect(status().isNoContent());
        assertThat(user("ana@example.com")).isNull();
    }

    @Test
    void aBlankPasswordIs400() throws Exception {
        String token = signup("ana@example.com");
        delete(token, "{\"currentPassword\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(user("ana@example.com")).isNotNull();
    }

    @Test
    void aCookieSessionWithoutTheClientHeaderIs403() throws Exception {
        String setCookie =
                signup("ana@example.com", "web")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        Cookie cookie =
                new Cookie(
                        "fluveny_session",
                        setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';')));
        this.mvc
                .perform(
                        post(DELETION)
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"senha123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLIENT_HEADER_REQUIRED"));
        assertThat(user("ana@example.com")).isNotNull();
    }

    @Test
    void anAccountAlreadyMarkedIs404() throws Exception {
        String token = signup("ana@example.com");
        this.mongo
                .getCollection("users")
                .updateOne(
                        new Document("email", "ana@example.com"),
                        new Document("$set", new Document("deletionRequestedAt", new Date())));
        deleteWith(token, "senha123")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(user("ana@example.com")).isNotNull();
    }

    /** O e-mail pendente que virou de outra conta: nada dessa conta é apagado. */
    @Test
    void aPendingEmailOwnedByAnotherAccountIsLeftAlone() throws Exception {
        String token = signup("ana@example.com");
        this.mvc
                .perform(
                        post("/api/v1/students/me/email")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("newEmail", "bia@example.com")
                                                .put("currentPassword", "senha123")
                                                .toString()))
                .andExpect(status().is2xxSuccessful());
        signup("bia@example.com");
        login("bia@example.com", "errada99").andExpect(status().isUnauthorized());
        long biaMails =
                this.mongo
                        .getCollection("mail_outbox")
                        .countDocuments(new Document("to", "bia@example.com"));
        assertThat(biaMails).isPositive();
        long biaThrottle =
                all("login_throttle").stream()
                        .filter(entry -> !"ip".equals(entry.getString("kind")))
                        .filter(entry ->
                                throttleKey("bia@example.com")
                                        .equals(entry.getString("accountKeyHash")))
                        .count();
        assertThat(biaThrottle).isEqualTo(2);

        deleteWith(token, "senha123").andExpect(status().isNoContent());

        assertThat(
                        this.mongo
                                .getCollection("mail_outbox")
                                .countDocuments(new Document("to", "bia@example.com")))
                .isEqualTo(biaMails);
        assertThat(
                        all("login_throttle").stream()
                                .filter(entry ->
                                        throttleKey("bia@example.com")
                                                .equals(entry.getString("accountKeyHash")))
                                .count())
                .isEqualTo(biaThrottle);
        assertThat(user("bia@example.com")).isNotNull();
    }

    @Test
    void withoutASessionIs401() throws Exception {
        this.mvc
                .perform(
                        post(DELETION)
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"senha123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
