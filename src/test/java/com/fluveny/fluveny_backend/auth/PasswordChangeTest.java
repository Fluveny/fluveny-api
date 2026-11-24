package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.node.ObjectNode;

/** A troca de senha com a senha atual (história 2.9), sem Mongo. */
@ExtendWith(OutputCaptureExtension.class)
class PasswordChangeTest extends AccountChangeTestSupport {

    private static final String PATH = "/api/v1/students/me/password";

    private static ObjectNode body(String current, String next) {
        return MAPPER.createObjectNode().put("currentPassword", current).put("newPassword", next);
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

    @Test
    void theChangeKeepsTheCurrentSessionAndEndsTheOthers() throws Exception {
        String current = signup("ana.senha@example.com");
        String other = login("ana.senha@example.com", PASSWORD);
        String userId = this.accounts.users.values().iterator().next().id();
        this.accounts.tokens.put(
                "reset-hash",
                new OneTimeToken(
                        "reset-hash",
                        userId,
                        TokenPurpose.PASSWORD_RESET,
                        "ana.senha@example.com",
                        NOW,
                        NOW.plus(Duration.ofHours(1))));

        send(PATH, current, body(PASSWORD, "outraSenha9")).andExpect(status().isNoContent());

        assertThat(sessionStatus(current)).isEqualTo(200);
        assertThat(sessionStatus(other)).isEqualTo(401);
        assertThat(this.accounts.tokens.values())
                .noneMatch(t -> t.purpose() == TokenPurpose.PASSWORD_RESET);
        login("ana.senha@example.com", "outraSenha9");
        expectProblem(
                this.mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/auth/login")
                                .header("X-Fluveny-Client", "android")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        MAPPER.createObjectNode()
                                                .put("email", "ana.senha@example.com")
                                                .put("password", PASSWORD)
                                                .toString())),
                401,
                "INVALID_CREDENTIALS");
    }

    @Test
    void theCookieSessionIsKeptToo() throws Exception {
        String bearer = signup("ana.cookie@example.com");
        String cookie = login("ana.cookie@example.com", PASSWORD);
        this.mvc
                .perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        PATH)
                                .cookie(new Cookie("fluveny_session", cookie))
                                .header("X-Fluveny-Client", "web")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(PASSWORD, "outraSenha9").toString()))
                .andExpect(status().isNoContent());
        assertThat(sessionStatus(cookie)).isEqualTo(200);
        assertThat(sessionStatus(bearer)).isEqualTo(401);
    }

    @Test
    void aWrongCurrentPasswordIs400AndTheSixthTryIs429() throws Exception {
        String token = signup("ana.errada@example.com");
        String hash = this.accounts.users.values().iterator().next().passwordHash();
        for (int i = 0; i < 5; i++) {
            expectProblem(
                    send(PATH, token, body("senhaErrada1", "outraSenha9")),
                    400,
                    "CURRENT_PASSWORD_INVALID");
        }
        // Bloqueado mesmo com a senha certa, e conta junto com a troca de e-mail.
        expectProblem(send(PATH, token, body(PASSWORD, "outraSenha9")), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
        expectProblem(
                send(
                        "/api/v1/students/me/email",
                        token,
                        MAPPER.createObjectNode()
                                .put("newEmail", "nova@example.com")
                                .put("currentPassword", PASSWORD)),
                429,
                "TOO_MANY_REQUESTS");
        assertThat(this.accounts.users.values().iterator().next().passwordHash()).isEqualTo(hash);
        assertThat(sessionStatus(token)).isEqualTo(200);

        this.clock.advance(Duration.ofMinutes(15));
        send(PATH, token, body(PASSWORD, "outraSenha9")).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"currentPassword\":\"senha123\",\"newPassword\":\"curta1\"}",
                "{\"currentPassword\":\"senha123\"}",
                "{\"newPassword\":\"outraSenha9\"}",
                "{\"currentPassword\":\"senha123\",\"newPassword\":\"outraSenha9\",\"x\":1}"
            })
    void anInvalidBodyIs400(String json) throws Exception {
        String token = signup("ana.corpo@example.com");
        expectProblem(
                this.mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        PATH)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)),
                400,
                "VALIDATION_FAILED");
    }

    @Test
    void withoutSessionIs401() throws Exception {
        expectProblem(send(PATH, null, body(PASSWORD, "outraSenha9")), 401, "UNAUTHENTICATED");
    }

    @Test
    void theLogNeverCarriesThePasswords(CapturedOutput output) throws Exception {
        String token = signup("ana.log@example.com");
        send(PATH, token, body("senhaErradaLog1", "senhaNovaLog9"))
                .andExpect(status().isBadRequest());
        send(PATH, token, body(PASSWORD, "senhaNovaLog9")).andExpect(status().isNoContent());
        assertThat(output.getAll())
                .doesNotContain("senhaErradaLog1")
                .doesNotContain("senhaNovaLog9")
                .doesNotContain(token)
                .doesNotContain(OpaqueTokens.hash(token));
    }

    @Test
    void parallelWrongPasswordsNeverPassTheLimit() throws Exception {
        String token = signup("ana.paralelo@example.com");
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(10);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results =
                    new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(
                        pool.submit(() ->
                                send(PATH, token, body("senhaErrada1", "outraSenha9"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus()));
            }
            int wrong = 0;
            for (var result : results) {
                if (result.get() == 400) {
                    wrong++;
                }
            }
            assertThat(wrong).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theRightPasswordResetsTheCounter() throws Exception {
        String token = signup("ana.zera@example.com");
        for (int i = 0; i < 4; i++) {
            send(PATH, token, body("senhaErrada1", "outraSenha9"))
                    .andExpect(status().isBadRequest());
        }
        send(PATH, token, body(PASSWORD, "outraSenha9")).andExpect(status().isNoContent());
        for (int i = 0; i < 5; i++) {
            expectProblem(
                    send(PATH, token, body("senhaErrada1", "maisUma9")),
                    400,
                    "CURRENT_PASSWORD_INVALID");
        }
        expectProblem(send(PATH, token, body("outraSenha9", "maisUma9")), 429, "TOO_MANY_REQUESTS");
    }

    @Test
    void theSamePasswordIs400AndNoSessionEnds() throws Exception {
        String token = signup("ana.igual@example.com");
        String other = login("ana.igual@example.com", PASSWORD);
        expectProblem(send(PATH, token, body(PASSWORD, PASSWORD)), 400, "VALIDATION_FAILED")
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.errors[0].field")
                                .value("newPassword"));
        assertThat(sessionStatus(other)).isEqualTo(200);
    }
}
