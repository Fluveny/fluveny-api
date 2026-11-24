package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.content.infrastructure.ContentIndexes;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.platform.events.GuardianConsentConfirmed;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * O adolescente com consentimento do responsável contra o Mongo (história 2.11, FR-2, NFR2): a
 * matriz do cadastro, a leitura e a recusa de todas as escritas do pendente, o reenvio, a
 * confirmação, a migração depois e a varredura de e-mail e token nas coleções e no log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocalMongoContainer.class, GuardianConsentIT.Events.class})
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class GuardianConsentIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern TOKEN =
            Pattern.compile("consentimento\\?token=([A-Za-z0-9_-]{43})");

    private static final String TEEN = "teen@example.com";

    private static final String GUARDIAN = "mae@example.com";

    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private ContentIndexes contentIndexes;

    @Autowired private PublishUnitVersion publisher;

    private String remoteAddr;

    @TestConfiguration(proxyBeanMethods = false)
    static class Events {

        static final List<GuardianConsentConfirmed> CONFIRMED = new CopyOnWriteArrayList<>();

        @Bean
        Recorder guardianConsentRecorder() {
            return new Recorder();
        }
    }

    static class Recorder {

        @EventListener
        void on(GuardianConsentConfirmed event) {
            Events.CONFIRMED.add(event);
        }
    }

    @BeforeEach
    void clean() {
        for (String collection : this.mongo.getCollectionNames()) {
            if (!collection.startsWith("system.")) {
                this.mongo.remove(new Query(), collection);
            }
        }
        Events.CONFIRMED.clear();
        int ip = IPS.incrementAndGet();
        this.remoteAddr = "10.37." + ((ip >> 8) & 255) + "." + (ip & 255);
    }

    private MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(this.remoteAddr);
            return r;
        });
    }

    private static LocalDate aged(int years) {
        return LocalDate.now().minusYears(years).minusDays(10);
    }

    private ResultActions signup(String email, LocalDate birthDate, String guardianEmail)
            throws Exception {
        ObjectNode body =
                MAPPER.createObjectNode()
                        .put("email", email)
                        .put("password", "senha123")
                        .put("birthDate", birthDate.toString())
                        .put("name", "Duda")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2");
        if (guardianEmail != null) {
            body.put("guardianEmail", guardianEmail);
        }
        return this.mvc.perform(
                from(post("/api/v1/auth/signup"))
                        .header("X-Fluveny-Client", "android")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()));
    }

    private String teen() throws Exception {
        return token(
                signup(TEEN, aged(15), GUARDIAN)
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.data.student.status")
                                        .value("PENDING_GUARDIAN_CONSENT")));
    }

    private static String token(ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString())
                .path("data")
                .path("sessionToken")
                .asString();
    }

    private ResultActions as(String session, MockHttpServletRequestBuilder request)
            throws Exception {
        return this.mvc.perform(
                from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session)
                        .header("X-Fluveny-Client", "android"));
    }

    private ResultActions confirm(String token) throws Exception {
        return this.mvc.perform(
                from(post("/api/v1/account/guardian-consent/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("token", token).toString()));
    }

    private ResultActions resend(String session) throws Exception {
        return as(session, post("/api/v1/students/me/guardian-consent/resend"));
    }

    private ResultActions resendTo(String session, String guardianEmail) throws Exception {
        return as(
                session,
                post("/api/v1/students/me/guardian-consent/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("guardianEmail", guardianEmail)
                                        .toString()));
    }

    private String linkFrom(String to, String pattern) {
        Pattern link = Pattern.compile(pattern + "\\?token=([A-Za-z0-9_-]{43})");
        String found = null;
        for (Document mail : all("mail_outbox")) {
            if (to.equals(mail.getString("to"))) {
                Matcher matcher = link.matcher(mail.getString("text"));
                if (matcher.find()) {
                    found = matcher.group(1);
                }
            }
        }
        assertThat(found).as("link %s para %s", pattern, to).isNotNull();
        return found;
    }

    /** A recuperação enfileira o e-mail num executor próprio: espera até 5 s. */
    private String resetLink() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            boolean queued =
                    all("mail_outbox").stream()
                            .anyMatch(m ->
                                    TEEN.equals(m.getString("to"))
                                            && m.getString("text").contains("redefinir-senha"));
            if (queued) {
                break;
            }
            Thread.sleep(100);
        }
        return linkFrom(TEEN, "redefinir-senha");
    }

    private ResultActions login(String email, String password) throws Exception {
        return this.mvc.perform(
                from(post("/api/v1/auth/login"))
                        .header("X-Fluveny-Client", "android")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("email", email)
                                        .put("password", password)
                                        .toString()));
    }

    private List<Document> all(String collection) {
        List<Document> found = new ArrayList<>();
        this.mongo.getCollection(collection).find().sort(new Document("createdAt", 1)).into(found);
        return found;
    }

    private Document user(String email) {
        return this.mongo.getCollection("users").find(new Document("email", email)).first();
    }

    private List<Document> guardianMails() {
        return all("mail_outbox").stream().filter(m -> GUARDIAN.equals(m.getString("to"))).toList();
    }

    private String lastGuardianToken() {
        Matcher matcher = TOKEN.matcher(guardianMails().getLast().getString("text"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    /** Simula o envio: o e-mail do responsável some do outbox ({@code purgeOnSend}). */
    private void sendMails() {
        this.mongo
                .getCollection("mail_outbox")
                .deleteMany(new Document("purgeOnSend", true).append("to", GUARDIAN));
    }

    @Test
    void aTeenWithAGuardianIsCreatedPendingWithTwoMails() throws Exception {
        teen();
        Document stored = user(TEEN);
        assertThat(stored.getString("status")).isEqualTo("PENDING_GUARDIAN_CONSENT");
        assertThat(stored.getString("guardianEmail")).isEqualTo(GUARDIAN);
        List<Document> mails = all("mail_outbox");
        assertThat(mails).hasSize(2);
        Document guardian = guardianMails().getFirst();
        assertThat(guardian.getString("priority")).isEqualTo("HIGH");
        assertThat(guardian.getBoolean("purgeOnSend")).isTrue();
        assertThat(guardian.getString("text")).contains("Duda", "/termos", "/consentimento?token=");
        assertThat(mails.stream().filter(m -> TEEN.equals(m.getString("to")))).hasSize(1);
        assertThat(all("one_time_tokens"))
                .anySatisfy(t -> assertThat(t.getString("purpose")).isEqualTo("GUARDIAN_CONSENT"));
    }

    @Test
    void aTeenWithoutAGuardianIs422AndNothingIsCreated() throws Exception {
        signup(TEEN, aged(15), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].field").value("guardianEmail"));
        assertThat(all("users")).isEmpty();
    }

    @Test
    void theTeensOwnEmailAsGuardianIs400() throws Exception {
        signup(TEEN, aged(15), "  TEEN@example.com ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("guardianEmail"));
        assertThat(all("users")).isEmpty();
    }

    @Test
    void underTwelveIsStill422() throws Exception {
        signup(TEEN, aged(11), GUARDIAN)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AGE_BELOW_MINIMUM"));
        assertThat(all("users")).isEmpty();
    }

    @Test
    void anAdultIgnoresTheGuardian() throws Exception {
        signup("adulto@example.com", aged(30), GUARDIAN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.student.status").value("ACTIVE"));
        Document stored = user("adulto@example.com");
        assertThat(stored.getString("status")).isEqualTo("ACTIVE");
        assertThat(stored.toJson()).doesNotContain(GUARDIAN);
        assertThat(guardianMails()).isEmpty();
    }

    @Test
    void thePendingTeenLogsInAndReads() throws Exception {
        String session = teen();
        this.mvc
                .perform(
                        from(post("/api/v1/auth/login"))
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("email", TEEN)
                                                .put("password", "senha123")
                                                .toString()))
                .andExpect(status().isOk());
        as(session, get("/api/v1/students/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_GUARDIAN_CONSENT"));
        as(session, get("/api/v1/students/me/dashboard")).andExpect(status().isOk());
        as(session, get("/api/v1/students/me/progress")).andExpect(status().isOk());
    }

    /** NFR2: toda escrita de Progresso recusa o pendente, sem gravar. */
    @Test
    void everyProgressWriteRefusesThePendingTeen() throws Exception {
        String session = teen();
        String any = "{}";
        List<MockHttpServletRequestBuilder> writes =
                List.of(
                        post("/api/v1/students/me/attempts"),
                        post("/api/v1/students/me/attempts/reveal"),
                        post("/api/v1/students/me/completions"),
                        post("/api/v1/students/me/events"),
                        put("/api/v1/students/me/dialogue-answers"),
                        post("/api/v1/students/me/visitor-migration"));
        for (MockHttpServletRequestBuilder write : writes) {
            ResultActions result =
                    as(session, write.contentType(MediaType.APPLICATION_JSON).content(any));
            int code = result.andReturn().getResponse().getStatus();
            // Corpo inválido dá 400 antes; com corpo válido, o 403 vem do mesmo ponto.
            assertThat(code).isIn(400, 403);
        }
        String attempt =
                MAPPER.createObjectNode()
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("versionId", "sha256:" + "a".repeat(64))
                        .put("activityKey", "s1-hi-alex")
                        .put("outcome", "VIEWED")
                        .put("studyTimeMs", 1000)
                        .toString();
        as(
                        session,
                        post("/api/v1/students/me/completions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(attempt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_PENDING"));
        String event =
                MAPPER.createObjectNode()
                        .put("type", "ACTIVITY_STARTED")
                        .put("versionId", "sha256:" + "a".repeat(64))
                        .put("activityKey", "s1-hi-alex")
                        .toString();
        as(
                        session,
                        post("/api/v1/students/me/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(event))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_PENDING"));
        as(
                        session,
                        put("/api/v1/students/me/dialogue-answers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("versionId", "sha256:" + "a".repeat(64))
                                                .put("activityKey", "s1-about-you")
                                                .put("name", "Duda")
                                                .toString()))
                .andExpect(status().isForbidden());
        as(
                        session,
                        post("/api/v1/students/me/visitor-migration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("deviceId", UUID.randomUUID().toString())
                                                .set(
                                                        "receipts",
                                                        MAPPER.createArrayNode().add("v1.a.b"))
                                                .toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_PENDING"));
        assertThat(all("activity_results")).isEmpty();
        assertThat(all("unit_progress")).isEmpty();
        // Perfil continua permitido.
        as(
                        session,
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                        "/api/v1/students/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"SHE\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void theResendReplacesTheLinkAndIsLimited() throws Exception {
        String session = teen();
        String first = lastGuardianToken();
        resend(session).andExpect(status().isAccepted());
        String second = lastGuardianToken();
        assertThat(second).isNotEqualTo(first);
        resend(session)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        confirm(first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_TOKEN_INVALID"));
        assertThat(user(TEEN).getString("status")).isEqualTo("PENDING_GUARDIAN_CONSENT");
    }

    @Test
    void theResendWithoutTheClientHeaderIs400() throws Exception {
        String session = teen();
        this.mvc
                .perform(
                        from(post("/api/v1/students/me/guardian-consent/resend"))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_HEADER_REQUIRED"));
    }

    @Test
    void anAdultResendIs409() throws Exception {
        String session =
                token(signup("adulto@example.com", aged(30), null).andExpect(status().isCreated()));
        resend(session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_NOT_PENDING"));
    }

    @Test
    void theConfirmationActivatesStoresOnlyHashesAndPublishes(CapturedOutput output)
            throws Exception {
        String session = teen();
        String token = lastGuardianToken();
        sendMails();
        confirm(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        Document stored = user(TEEN);
        assertThat(stored.getString("status")).isEqualTo("ACTIVE");
        assertThat(stored.containsKey("guardianEmail")).isFalse();
        List<Document> consents = all("guardian_consents");
        assertThat(consents).hasSize(1);
        Document consent = consents.getFirst();
        assertThat(consent.getString("userId")).isEqualTo(stored.getObjectId("_id").toHexString());
        assertThat(consent.getString("guardianEmailHash"))
                .isEqualTo(OpaqueTokens.hash(GuardianConsent.EMAIL_HASH_PREFIX + GUARDIAN));
        assertThat(consent.getDate("confirmedAt")).isNotNull();
        assertThat(consent.getString("termsVersion")).isEqualTo("0.3");
        assertThat(consent.getString("privacyVersion")).isEqualTo("0.4");
        assertThat(consent.getString("ipHash")).isNotBlank().doesNotContain(this.remoteAddr);
        assertThat(Events.CONFIRMED).hasSize(1);
        assertThat(Events.CONFIRMED.getFirst().toString()).doesNotContain(TEEN, GUARDIAN);

        // Nenhum e-mail do responsável em texto, nem o token, em nenhuma coleção.
        for (String collection : this.mongo.getCollectionNames()) {
            if (collection.startsWith("system.")) {
                continue;
            }
            for (Document document : all(collection)) {
                assertThat(document.toJson())
                        .as("dado do responsável em %s", collection)
                        .doesNotContain(GUARDIAN, token);
            }
        }
        // A sessão segue e agora grava.
        as(session, get("/api/v1/students/me"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(output.getAll()).doesNotContain(GUARDIAN, TEEN, token, session);

        // Reusar o link.
        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_TOKEN_INVALID"));
        resend(session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_NOT_PENDING"));
        assertThat(all("guardian_consents")).hasSize(1);
    }

    @Test
    void anExpiredLinkIs400AndChangesNothing() throws Exception {
        teen();
        String token = lastGuardianToken();
        this.mongo
                .getCollection("one_time_tokens")
                .updateMany(
                        new Document("purpose", "GUARDIAN_CONSENT"),
                        new Document(
                                "$set",
                                new Document(
                                        "expiresAt",
                                        new Date(System.currentTimeMillis() - 86_400_000L))));
        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_TOKEN_INVALID"));
        confirm("x".repeat(43)).andExpect(status().isBadRequest());
        assertThat(user(TEEN).getString("status")).isEqualTo("PENDING_GUARDIAN_CONSENT");
        assertThat(all("guardian_consents")).isEmpty();
    }

    @Test
    void afterTheConsentTheAppMigratesTheVisitorReceiptsOnce() throws Exception {
        String versionId = publish();
        UUID deviceId = UUID.randomUUID();
        List<String> receipts =
                List.of(
                        visitorCompletion(deviceId, versionId, "s1-hi-alex", "VIEWED"),
                        visitorCompletion(deviceId, versionId, "s1-greeting-cards", "VIEWED"),
                        "v1.adulterado.recibo");
        String session = teen();
        confirm(lastGuardianToken()).andExpect(status().isOk());

        ObjectNode body = MAPPER.createObjectNode().put("deviceId", deviceId.toString());
        receipts.forEach(body.putArray("receipts")::add);
        as(
                        session,
                        post("/api/v1/students/me/visitor-migration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.migrated").value(2))
                .andExpect(jsonPath("$.data.discarded").value(1));
        assertThat(all("activity_results")).hasSize(2);
        as(
                        session,
                        post("/api/v1/students/me/visitor-migration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.migrated").value(0))
                .andExpect(jsonPath("$.data.discarded").value(3));
        assertThat(all("activity_results")).hasSize(2);
    }

    /** NFR2: Tentativa e Ver resposta com corpo válido também recusam, sem gravar. */
    @Test
    void validAttemptAndRevealAreRefusedForThePendingTeen() throws Exception {
        String session = teen();
        for (String path :
                List.of("/api/v1/students/me/attempts", "/api/v1/students/me/attempts/reveal")) {
            ObjectNode body =
                    MAPPER.createObjectNode()
                            .put("attemptId", UUID.randomUUID().toString())
                            .put("versionId", "sha256:" + "a".repeat(64))
                            .put("activityKey", "s1-greetings")
                            .put("type", "choice")
                            .put("studyTimeMs", 1000);
            ObjectNode answer = body.putObject("answer");
            answer.put("type", "choice");
            answer.putArray("items")
                    .addObject()
                    .put("itemKey", "s1-greetings-hello")
                    .put("optionKey", "nice-to-meet-you-too");
            as(session, post(path).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GUARDIAN_CONSENT_PENDING"));
        }
        assertThat(all("activity_results")).isEmpty();
        assertThat(all("unit_progress")).isEmpty();
    }

    @Test
    void thePendingTeenChangesThePasswordAndTheEmail() throws Exception {
        String session = teen();
        as(
                        session,
                        post("/api/v1/students/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("currentPassword", "senha123")
                                                .put("newPassword", "novaSenha9")
                                                .toString()))
                .andExpect(status().is2xxSuccessful());
        login(TEEN, "novaSenha9").andExpect(status().isOk());

        as(
                        session,
                        post("/api/v1/students/me/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("newEmail", "teen.novo@example.com")
                                                .put("currentPassword", "novaSenha9")
                                                .toString()))
                .andExpect(status().is2xxSuccessful());
        this.mvc
                .perform(
                        from(post("/api/v1/account/email-change/confirm"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put(
                                                        "token",
                                                        linkFrom(
                                                                "teen.novo@example.com",
                                                                "confirmar-novo-email"))
                                                .toString()))
                .andExpect(status().is2xxSuccessful());
        Document stored = user("teen.novo@example.com");
        assertThat(stored.getString("status")).isEqualTo("PENDING_GUARDIAN_CONSENT");
        assertThat(stored.getString("guardianEmail")).isEqualTo(GUARDIAN);
    }

    @Test
    void thePendingTeenRecoversThePassword() throws Exception {
        teen();
        this.mvc
                .perform(
                        from(post("/api/v1/account/email-confirmation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("token", linkFrom(TEEN, "confirmar-email"))
                                                .toString()))
                .andExpect(status().isOk());
        this.mvc
                .perform(
                        from(post("/api/v1/auth/password-recovery"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(MAPPER.createObjectNode().put("email", TEEN).toString()))
                .andExpect(status().isAccepted());
        this.mvc
                .perform(
                        from(post("/api/v1/auth/password-reset"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("token", resetLink())
                                                .put("password", "novaSenha9")
                                                .toString()))
                .andExpect(status().isNoContent());
        login(TEEN, "novaSenha9").andExpect(status().isOk());
    }

    @Test
    void theResendFixesAWrongGuardianEmail() throws Exception {
        String session = teen();
        String old = lastGuardianToken();
        resendTo(session, " Pai@Example.com ").andExpect(status().isAccepted());
        assertThat(user(TEEN).getString("guardianEmail")).isEqualTo("pai@example.com");
        confirm(old).andExpect(status().isBadRequest());
        confirm(linkFrom("pai@example.com", "consentimento")).andExpect(status().isOk());
        assertThat(user(TEEN).getString("status")).isEqualTo("ACTIVE");
        assertThat(all("guardian_consents").getFirst().getString("guardianEmailHash"))
                .isEqualTo(
                        OpaqueTokens.hash(GuardianConsent.EMAIL_HASH_PREFIX + "pai@example.com"));
    }

    @Test
    void theResendRefusesTheTeensOwnEmailAndAMissingGuardian() throws Exception {
        String session = teen();
        resendTo(session, TEEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("guardianEmail"));
        this.mongo
                .getCollection("users")
                .updateOne(
                        new Document("email", TEEN),
                        new Document("$unset", new Document("guardianEmail", "")));
        resend(session)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardianEmail"));
        resendTo(session, GUARDIAN).andExpect(status().isAccepted());
        assertThat(user(TEEN).getString("guardianEmail")).isEqualTo(GUARDIAN);
    }

    private String publish() throws Exception {
        this.mongo.dropCollection("unit_versions");
        this.mongo.dropCollection("unit_pointers");
        this.contentIndexes.ensure();
        Path root = Path.of(GuardianConsentIT.class.getResource("/content-fixtures/valid").toURI());
        return this.publisher
                .publish(
                        "unit-1",
                        UnitBundle.fromDirectory(
                                root.resolve("units/unit-1"), root.resolve("media.json")),
                        true,
                        TypeCheck.SKIP)
                .versionId();
    }

    private String visitorCompletion(
            UUID deviceId, String versionId, String activityKey, String outcome) throws Exception {
        ObjectNode body =
                MAPPER.createObjectNode()
                        .put("activityKey", activityKey)
                        .put("outcome", outcome)
                        .put("deviceId", deviceId.toString())
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("versionId", versionId)
                        .put("studyTimeMs", 1500);
        JsonNode response =
                MAPPER.readTree(
                        this.mvc
                                .perform(
                                        from(post("/api/v1/visitor/completions"))
                                                .header("X-Fluveny-Client", "android")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body.toString()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        return response.at("/data/receipt").asString();
    }
}
