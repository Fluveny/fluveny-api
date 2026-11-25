package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * A limpeza da homologação da 2.11 (história 2.12) contra o Mongo: monta o que a 2.11 deixava (uma
 * conta pendente com sessão, tokens e e-mails, o {@code guardianEmail}, o token e o e-mail do
 * responsável e a coleção {@code guardian_consents}), roda a limpeza duas vezes e confere que nada
 * sobra e que o adulto fica.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class GuardianConsentCleanupIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private GuardianConsentCleanup cleanup;

    @BeforeEach
    void clean() {
        for (String collection : this.mongo.getCollectionNames()) {
            if (!collection.startsWith("system.")) {
                this.mongo.remove(new Query(), collection);
            }
        }
    }

    private void signup(String email) throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", email)
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
    }

    private ObjectId idOf(String email) {
        return this.mongo
                .findOne(Query.query(Criteria.where("email").is(email)), Document.class, "users")
                .getObjectId("_id");
    }

    @Test
    void removesWhatTheGuardianConsentLeftAndRunsTwice() throws Exception {
        signup("adulto@example.com");
        signup("teen@example.com");
        ObjectId adult = idOf("adulto@example.com");
        ObjectId teen = idOf("teen@example.com");
        this.mongo.updateFirst(
                Query.query(Criteria.where("_id").is(teen)),
                new Update()
                        .set("status", GuardianConsentCleanup.PENDING_STATUS)
                        .set("guardianEmail", "mae@example.com"),
                "users");
        this.mongo.updateFirst(
                Query.query(Criteria.where("_id").is(adult)),
                new Update().set("guardianEmail", "sobra@example.com"),
                "users");
        this.mongo.insert(
                new Document("tokenHash", "a".repeat(64))
                        .append("userId", teen)
                        .append("purpose", GuardianConsentCleanup.TOKEN_PURPOSE)
                        .append("expiresAt", new Date(System.currentTimeMillis() + 86_400_000L)),
                "one_time_tokens");
        this.mongo.insert(
                new Document("to", "mae@example.com")
                        .append("subject", GuardianConsentCleanup.CONSENT_SUBJECT)
                        .append("text", "link")
                        .append("createdAt", new Date()),
                "mail_outbox");
        this.mongo.insert(
                new Document("userId", teen).append("guardianEmail", "mae@example.com"),
                GuardianConsentCleanup.CONSENTS_COLLECTION);

        this.cleanup.run();
        this.cleanup.run();

        assertThat(this.mongo.findById(teen, Document.class, "users")).isNull();
        Document kept = this.mongo.findById(adult, Document.class, "users");
        assertThat(kept).isNotNull();
        assertThat(kept).doesNotContainKey("guardianEmail");
        assertThat(
                        this.mongo.count(
                                Query.query(
                                        Criteria.where("status").is("PENDING_GUARDIAN_CONSENT")),
                                "users"))
                .isZero();
        assertThat(
                        this.mongo.count(
                                Query.query(Criteria.where("userId").is(teen)), "one_time_tokens"))
                .isZero();
        assertThat(
                        this.mongo.count(
                                Query.query(
                                        Criteria.where("purpose")
                                                .is(GuardianConsentCleanup.TOKEN_PURPOSE)),
                                "one_time_tokens"))
                .isZero();
        assertThat(this.mongo.count(Query.query(Criteria.where("userId").is(teen)), "sessions"))
                .isZero();
        assertThat(this.mongo.count(Query.query(Criteria.where("userId").is(adult)), "sessions"))
                .isOne();
        assertThat(
                        this.mongo.count(
                                Query.query(
                                        Criteria.where("subject")
                                                .is(GuardianConsentCleanup.CONSENT_SUBJECT)),
                                "mail_outbox"))
                .isZero();
        assertThat(
                        this.mongo.count(
                                Query.query(Criteria.where("to").is("teen@example.com")),
                                "mail_outbox"))
                .isZero();
        assertThat(this.mongo.collectionExists(GuardianConsentCleanup.CONSENTS_COLLECTION))
                .isFalse();
    }

    @Test
    void aPendingAccountWithoutAValidMetricsIdIsDeletedAndTheOthersToo() throws Exception {
        signup("a@example.com");
        signup("b@example.com");
        ObjectId broken = idOf("a@example.com");
        ObjectId fine = idOf("b@example.com");
        this.mongo.updateFirst(
                Query.query(Criteria.where("_id").is(broken)),
                new Update()
                        .set("status", GuardianConsentCleanup.PENDING_STATUS)
                        .set("metricsId", "nao-e-uuid"),
                "users");
        this.mongo.updateFirst(
                Query.query(Criteria.where("_id").is(fine)),
                new Update().set("status", GuardianConsentCleanup.PENDING_STATUS),
                "users");

        this.cleanup.run();
        this.cleanup.run();

        assertThat(this.mongo.findById(broken, Document.class, "users")).isNull();
        assertThat(this.mongo.findById(fine, Document.class, "users")).isNull();
        assertThat(this.mongo.count(Query.query(Criteria.where("userId").is(fine)), "sessions"))
                .isZero();
    }
}
