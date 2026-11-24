package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Dados da conta contra o Mongo 8 (história 2.9): o perfil (país sai do documento ao limpar), a
 * troca de senha que mantém só a sessão da requisição, a troca de e-mail com o token {@code
 * EMAIL_CHANGE} só como hash, e a corrida da confirmação decidida pelo índice único do e-mail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class EmailChangeIT {

    private static final Pattern TOKEN =
            Pattern.compile("confirmar-novo-email\\?token=([A-Za-z0-9_-]{43})");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

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
        return token(
                this.mvc.perform(
                        post("/api/v1/auth/signup")
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)));
    }

    private String login(String email) throws Exception {
        return token(
                this.mvc.perform(
                        post("/api/v1/auth/login")
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("email", email)
                                                .put("password", "senha123")
                                                .toString())));
    }

    private static String token(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private ResultActions send(String path, String token, ObjectNode body) throws Exception {
        return this.mvc.perform(
                post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()));
    }

    private ResultActions requestChange(String token, String newEmail) throws Exception {
        return send(
                "/api/v1/students/me/email",
                token,
                MAPPER.createObjectNode()
                        .put("newEmail", newEmail)
                        .put("currentPassword", "senha123"));
    }

    private ResultActions confirm(String link) throws Exception {
        return this.mvc.perform(
                post("/api/v1/account/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("token", link).toString()));
    }

    private int sessionStatus(String token) throws Exception {
        return this.mvc
                .perform(
                        get("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Document user(String email) {
        return this.mongo.getCollection("users").find(new Document("email", email)).first();
    }

    private String lastLink() {
        List<Document> mails = new ArrayList<>();
        this.mongo.getCollection("mail_outbox").find().into(mails);
        for (Document mail : mails.reversed()) {
            Matcher matcher = TOKEN.matcher(mail.getString("text"));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("sem link de troca no outbox");
    }

    @Test
    void theProfileUpdateWritesAndClearsTheFields() throws Exception {
        String token = signup("ana@example.com");
        this.mvc
                .perform(
                        patch("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Bia\",\"country\":\"Brazil\","
                                                + "\"pronounSet\":\"HE\"}"))
                .andExpect(status().isOk());
        Document stored = user("ana@example.com");
        assertThat(stored.getString("name")).isEqualTo("Bia");
        assertThat(stored.getString("country")).isEqualTo("Brazil");
        assertThat(stored.getString("pronounSet")).isEqualTo("HE");
        this.mvc
                .perform(
                        patch("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"country\":null,\"pronounSet\":null}"))
                .andExpect(status().isOk());
        stored = user("ana@example.com");
        assertThat(stored.containsKey("country")).isFalse();
        assertThat(stored.getString("pronounSet")).isNull();
        assertThat(stored.getString("name")).isEqualTo("Bia");
    }

    @Test
    void thePasswordChangeKeepsOnlyTheCurrentSession() throws Exception {
        String current = signup("ana@example.com");
        String other = login("ana@example.com");
        String bia = signup("bia@example.com");
        send(
                        "/api/v1/students/me/password",
                        current,
                        MAPPER.createObjectNode()
                                .put("currentPassword", "senha123")
                                .put("newPassword", "outraSenha9"))
                .andExpect(status().isNoContent());
        assertThat(sessionStatus(current)).isEqualTo(200);
        assertThat(sessionStatus(other)).isEqualTo(401);
        assertThat(sessionStatus(bia)).isEqualTo(200);
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isEqualTo(2);
    }

    @Test
    void theChangeStoresOnlyTheHashAndTheConfirmationSwapsTheEmail() throws Exception {
        String token = signup("ana@example.com");
        requestChange(token, "nova@example.com").andExpect(status().isAccepted());
        String link = lastLink();
        Document stored =
                this.mongo
                        .getCollection("one_time_tokens")
                        .find(new Document("purpose", "EMAIL_CHANGE"))
                        .first();
        assertThat(stored.getString("tokenHash")).isEqualTo(OpaqueTokens.hash(link));
        assertThat(stored.toJson()).doesNotContain(link);
        assertThat(user("ana@example.com").getString("pendingEmail")).isEqualTo("nova@example.com");
        assertThat(
                        this.mongo
                                .getCollection("mail_outbox")
                                .countDocuments(new Document("priority", "HIGH")))
                .isEqualTo(2);

        confirm(link).andExpect(status().isNoContent());
        Document swapped = user("nova@example.com");
        assertThat(swapped).isNotNull();
        assertThat(swapped.get("pendingEmail")).isNull();
        assertThat(swapped.getDate("emailVerifiedAt")).isNotNull();
        assertThat(user("ana@example.com")).isNull();
        assertThat(this.mongo.getCollection("one_time_tokens").countDocuments()).isZero();
        assertThat(sessionStatus(token)).isEqualTo(200);
        login("nova@example.com");
        this.mvc
                .perform(
                        get("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.email").value("nova@example.com"));
        confirm(link).andExpect(status().isGone());
    }

    /** O índice único decide: o cadastro do e-mail novo chega antes do clique. */
    @Test
    void theUniqueIndexDecidesTheRace() throws Exception {
        String token = signup("ana@example.com");
        requestChange(token, "nova@example.com").andExpect(status().isAccepted());
        String link = lastLink();
        signup("nova@example.com");
        confirm(link)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        Document ana = user("ana@example.com");
        assertThat(ana).isNotNull();
        assertThat(ana.get("pendingEmail")).isNull();
        assertThat(
                        this.mongo
                                .getCollection("users")
                                .countDocuments(new Document("email", "nova@example.com")))
                .isEqualTo(1);
    }
}
