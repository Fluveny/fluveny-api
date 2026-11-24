package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.node.ObjectNode;

/** As respostas do Diálogo gravadas no perfil (história 2.9, FR-13), sem Mongo. */
class DialogueAnswersTest extends StudentLearningTestSupport {

    static final String ANSWERS = BASE + "/dialogue-answers";

    private ResultActions answer(ObjectNode body) throws Exception {
        return answer(body, this.token);
    }

    private ResultActions answer(ObjectNode body, String token) throws Exception {
        var request = put(ANSWERS).contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return this.mvc.perform(request);
    }

    private ObjectNode body(String activityKey) {
        return MAPPER.createObjectNode()
                .put("versionId", this.versionId)
                .put("activityKey", activityKey);
    }

    private User me() {
        return this.accounts.users.get(this.userId);
    }

    @Test
    void theNameAndTheCountryGoToTheProfile() throws Exception {
        answer(body(DIALOGUE).put("name", " Bia ").put("country", "Brazil"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        this.mvc
                .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(jsonPath("$.data.name").value("Bia"))
                .andExpect(jsonPath("$.data.country").value("Brazil"))
                .andExpect(jsonPath("$.data.pronounSet").value("SHE"));
    }

    @Test
    void onlyTheNameKeepsTheCountry() throws Exception {
        answer(body(DIALOGUE).put("country", "Brazil")).andExpect(status().isNoContent());
        answer(body(DIALOGUE).put("name", "Bia")).andExpect(status().isNoContent());
        assertThat(me().name()).isEqualTo("Bia");
        assertThat(me().country()).isEqualTo("Brazil");
    }

    /** O Diálogo só com {@code ask: name} não grava o país. */
    @Test
    void aFieldTheDialogueDoesNotAskIs400() throws Exception {
        ObjectNode bundle = validBundle();
        ObjectNode dialogue = (ObjectNode) bundle.at("/stages/0/activities/1");
        assertThat(dialogue.get("activityKey").asString()).isEqualTo(DIALOGUE);
        ((ObjectNode) dialogue.at("/lines/2")).remove("ask");
        this.versionId = publish(bundle);
        expectProblem(answer(body(DIALOGUE).put("country", "Brazil")), 400, "VALIDATION_FAILED");
        expectProblem(
                answer(body(DIALOGUE).put("name", "Bia").put("country", "Brazil")),
                400,
                "VALIDATION_FAILED");
        assertThat(me().country()).isNull();
        assertThat(me().name()).isEqualTo("Ana Secreta");
        answer(body(DIALOGUE).put("name", "Bia")).andExpect(status().isNoContent());
    }

    @Test
    void anActivityThatIsNotADialogueOrDoesNotExistIs400() throws Exception {
        expectProblem(answer(body("s1-greetings").put("name", "Bia")), 400, "VALIDATION_FAILED");
        expectProblem(answer(body("s1-nao-existe").put("name", "Bia")), 400, "VALIDATION_FAILED");
        assertThat(me().name()).isEqualTo("Ana Secreta");
    }

    @Test
    void aCountryOutOfTheListIs400() throws Exception {
        expectProblem(answer(body(DIALOGUE).put("country", "XX")), 400, "VALIDATION_FAILED");
        assertThat(me().country()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "blank", "extra", "noVersion"})
    void anInvalidBodyIs400(String kind) throws Exception {
        ObjectNode body = body(DIALOGUE);
        switch (kind) {
            case "blank" -> body.put("name", "  ");
            case "extra" -> body.put("name", "Bia").put("pronounSet", "HE");
            case "noVersion" -> body.put("name", "Bia").remove("versionId");
            default -> {}
        }
        expectProblem(answer(body), 400, "VALIDATION_FAILED");
        assertThat(me().name()).isEqualTo("Ana Secreta");
    }

    @Test
    void withoutSessionIs401() throws Exception {
        expectProblem(answer(body(DIALOGUE).put("name", "Bia"), null), 401, "UNAUTHENTICATED");
    }

    @Test
    void spacesAroundAValidNameDoNotCountInTheLimit() throws Exception {
        String name = "a".repeat(60);
        answer(body(DIALOGUE).put("name", "   " + name + "   ")).andExpect(status().isNoContent());
        assertThat(me().name()).isEqualTo(name);
    }
}
