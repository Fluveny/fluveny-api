package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code PATCH /students/me} com nome, país e pronomes (história 2.9), a troca de pronomes na
 * Tentativa seguinte e o {@code X-Fluveny-Client} nas rotas novas, sem Mongo.
 */
class StudentProfileUpdateTest extends StudentLearningTestSupport {

    private ResultActions patchMe(String json) throws Exception {
        return this.mvc.perform(
                patch(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private User me() {
        return this.accounts.users.get(this.userId);
    }

    @Test
    void nameAndCountryChangeAndThePronounsStay() throws Exception {
        patchMe("{\"name\":\" Bia \",\"country\":\"Brazil\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Bia"))
                .andExpect(jsonPath("$.data.country").value("Brazil"))
                .andExpect(jsonPath("$.data.pronounSet").value("SHE"));
        assertThat(me().pronounSet()).isEqualTo(PronounSet.SHE);
        this.mvc
                .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(jsonPath("$.data.name").value("Bia"))
                .andExpect(jsonPath("$.data.country").value("Brazil"));
    }

    @Test
    void anExplicitNullClearsAndAnAbsentFieldStays() throws Exception {
        patchMe("{\"country\":\"Brazil\"}").andExpect(status().isOk());
        patchMe("{\"name\":null,\"pronounSet\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").isEmpty())
                .andExpect(jsonPath("$.data.pronounSet").isEmpty())
                .andExpect(jsonPath("$.data.country").value("Brazil"));
        patchMe("{\"country\":null}").andExpect(status().isOk());
        assertThat(me().country()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"country\":\"XX\"}",
                "{\"country\":\"brazil\"}",
                "{}",
                "{\"foo\":1}",
                "{\"name\":\"Bia\",\"foo\":1}",
                "{\"pronounSet\":\"IT\"}"
            })
    void anInvalidBodyIs400AndNothingIsStored(String json) throws Exception {
        expectProblem(patchMe(json), 400, "VALIDATION_FAILED");
        assertThat(me().name()).isEqualTo("Ana Secreta");
        assertThat(me().country()).isNull();
        assertThat(me().pronounSet()).isEqualTo(PronounSet.SHE);
    }

    @Test
    void aNameAbove60Is400() throws Exception {
        expectProblem(patchMe("{\"name\":\"" + "a".repeat(61) + "\"}"), 400, "VALIDATION_FAILED");
    }

    /** O corretor lê o pronome na hora: a troca vale na Tentativa seguinte. */
    @Test
    void theNextAttemptUsesTheNewPronouns() throws Exception {
        ObjectNode bundle = validBundle();
        ObjectNode speech = (ObjectNode) bundle.at("/stages/0/activities/4");
        assertThat(speech.get("activityKey").asString()).isEqualTo(SPEECH);
        speech.putObject("target")
                .put("he", "He is my friend.")
                .put("she", "She is my friend.")
                .put("they", "They are my friend.");
        this.versionId = publish(bundle);
        patchMe("{\"pronounSet\":\"HE\"}").andExpect(status().isOk());
        send(ATTEMPTS, speech("he is my friend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expected.transcript").value("He is my friend."));
        patchMe("{\"pronounSet\":\"SHE\"}").andExpect(status().isOk());
        send(ATTEMPTS, speech("she is my friend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expected.transcript").value("She is my friend."));
    }

    private ObjectNode speech(String transcript) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", SPEECH);
        body.put("type", "speech");
        body.putObject("answer").put("type", "speech").put("transcript", transcript);
        body.put("studyTimeMs", 3000);
        return body;
    }

    /** Toda rota nova de escrita, com cookie e sem X-Fluveny-Client, dá 403. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "PATCH /api/v1/students/me",
                "PUT /api/v1/students/me/dialogue-answers",
                "POST /api/v1/students/me/password",
                "POST /api/v1/students/me/email"
            })
    void aCookieWithoutTheClientHeaderIs403(String route) throws Exception {
        String[] parts = route.split(" ");
        MockHttpServletRequestBuilder request =
                switch (parts[0]) {
                    case "PATCH" -> patch(parts[1]);
                    case "PUT" -> put(parts[1]);
                    default -> post(parts[1]);
                };
        expectProblem(
                this.mvc.perform(
                        request.cookie(new Cookie("fluveny_session", this.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")),
                403,
                "CLIENT_HEADER_REQUIRED");
    }

    @Test
    void theProfileToStringHasNoPersonalData() {
        String text =
                new com.fluveny.fluveny_backend.account.StudentProfile(
                                "Ana",
                                "ana@example.com",
                                "Brazil",
                                PronounSet.SHE,
                                true,
                                false,
                                "nova@example.com",
                                "ACTIVE")
                        .toString();
        assertThat(text).doesNotContain("Ana", "ana@example.com", "Brazil", "nova@example.com");
    }
}
