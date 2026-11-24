package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.TestAppUrl;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.node.ObjectNode;

/** A troca de e-mail com a senha atual e a confirmação pelo link (história 2.9), sem Mongo. */
@ExtendWith(OutputCaptureExtension.class)
class EmailChangeTest extends AccountChangeTestSupport {

    private static final String REQUEST = "/api/v1/students/me/email";

    private static final String CONFIRM = "/api/v1/account/email-change/confirm";

    private static final Pattern TOKEN =
            Pattern.compile(
                    Pattern.quote(TestAppUrl.URL + "/confirmar-novo-email?token=")
                            + "([A-Za-z0-9_-]{43})");

    private ResultActions request(String token, String newEmail, String password) throws Exception {
        return send(
                REQUEST,
                token,
                MAPPER.createObjectNode()
                        .put("newEmail", newEmail)
                        .put("currentPassword", password));
    }

    private ResultActions confirm(String token) throws Exception {
        return send(CONFIRM, null, MAPPER.createObjectNode().put("token", token));
    }

    private List<OutboxMail> mails(String to) {
        return this.accounts.outbox.all().stream().filter(mail -> mail.to().equals(to)).toList();
    }

    private String lastLink(String to) {
        List<OutboxMail> mails = mails(to);
        Matcher matcher = TOKEN.matcher(mails.getLast().text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private User user(String email) {
        return this.accounts.users.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aRequestStoresThePendingEmailAndQueuesTwoHighMails() throws Exception {
        String token = signup("ana@example.com");
        request(token, "  Nova@Example.com ", PASSWORD).andExpect(status().isAccepted());

        assertThat(user("ana@example.com").pendingEmail()).isEqualTo("nova@example.com");
        List<OutboxMail> toNew = mails("nova@example.com");
        assertThat(toNew).hasSize(1);
        assertThat(toNew.getFirst().priority()).isEqualTo(MailPriority.HIGH);
        assertThat(toNew.getFirst().subject()).isEqualTo("Confirme seu novo e-mail do Fluveny");
        List<OutboxMail> toOld =
                mails("ana@example.com").stream()
                        .filter(mail -> mail.priority() == MailPriority.HIGH)
                        .toList();
        assertThat(toOld).hasSize(1);
        assertThat(toOld.getFirst().text())
                .contains("Pediram para trocar o e-mail da sua conta para n***@e***.")
                .contains("Se não foi você, troque sua senha.")
                .doesNotContain("http")
                .doesNotContain("nova@example.com");
        String link = lastLink("nova@example.com");
        assertThat(this.accounts.tokens).containsKey(OpaqueTokens.hash(link));
        assertThat(this.accounts.tokens.get(OpaqueTokens.hash(link)).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(24)));
        this.mvc
                .perform(
                        get("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.email").value("ana@example.com"))
                .andExpect(jsonPath("$.data.pendingEmail").value("nova@example.com"));
    }

    @Test
    void theConfirmationSwapsTheEmailKeepsSessionsAndLogsInWithTheNewOne() throws Exception {
        String token = signup("ana@example.com");
        request(token, "nova@example.com", PASSWORD).andExpect(status().isAccepted());
        confirm(lastLink("nova@example.com")).andExpect(status().isNoContent());

        User user = user("nova@example.com");
        assertThat(user.pendingEmail()).isNull();
        assertThat(user.emailVerifiedAt()).isEqualTo(NOW);
        assertThat(this.accounts.tokens.values())
                .noneMatch(t ->
                        t.purpose() == TokenPurpose.EMAIL_CONFIRMATION
                                || t.purpose() == TokenPurpose.PASSWORD_RESET
                                || t.purpose() == TokenPurpose.EMAIL_CHANGE);
        this.mvc
                .perform(
                        get("/api/v1/students/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("nova@example.com"))
                .andExpect(jsonPath("$.data.emailConfirmed").value(true))
                .andExpect(jsonPath("$.data.pendingEmail").isEmpty());
        login("nova@example.com", PASSWORD);
    }

    @Test
    void aUsedOrReplacedLinkIs410() throws Exception {
        String token = signup("ana@example.com");
        request(token, "primeira@example.com", PASSWORD).andExpect(status().isAccepted());
        String first = lastLink("primeira@example.com");
        this.clock.advance(Duration.ofMinutes(1));
        request(token, "segunda@example.com", PASSWORD).andExpect(status().isAccepted());
        expectProblem(confirm(first), 410, "EMAIL_CHANGE_LINK_INVALID");
        assertThat(user("ana@example.com").pendingEmail()).isEqualTo("segunda@example.com");

        String second = lastLink("segunda@example.com");
        confirm(second).andExpect(status().isNoContent());
        expectProblem(confirm(second), 410, "EMAIL_CHANGE_LINK_INVALID");
        expectProblem(confirm("A".repeat(43)), 410, "EMAIL_CHANGE_LINK_INVALID");
    }

    @Test
    void anExpiredLinkIs410() throws Exception {
        String token = signup("ana@example.com");
        request(token, "nova@example.com", PASSWORD).andExpect(status().isAccepted());
        this.clock.advance(Duration.ofHours(24));
        expectProblem(confirm(lastLink("nova@example.com")), 410, "EMAIL_CHANGE_LINK_INVALID");
        assertThat(user("ana@example.com").email()).isEqualTo("ana@example.com");
    }

    @Test
    void theEmailOfAnotherAccountIs409AndNothingIsStored() throws Exception {
        signup("outra@example.com");
        String token = signup("ana@example.com");
        int mails = this.accounts.outbox.all().size();
        expectProblem(
                request(token, "Outra@example.com", PASSWORD), 409, "EMAIL_ALREADY_REGISTERED");
        assertThat(user("ana@example.com").pendingEmail()).isNull();
        assertThat(this.accounts.outbox.all()).hasSize(mails);
    }

    @Test
    void theCurrentEmailIs400() throws Exception {
        String token = signup("ana@example.com");
        expectProblem(request(token, "ANA@example.com", PASSWORD), 400, "VALIDATION_FAILED");
    }

    @Test
    void aWrongPasswordIs400AndNothingIsStored() throws Exception {
        String token = signup("ana@example.com");
        expectProblem(
                request(token, "nova@example.com", "senhaErrada1"),
                400,
                "CURRENT_PASSWORD_INVALID");
        assertThat(user("ana@example.com").pendingEmail()).isNull();
        assertThat(mails("nova@example.com")).isEmpty();
    }

    @Test
    void anInvalidBodyIs400() throws Exception {
        String token = signup("ana@example.com");
        expectProblem(request(token, "nao-e-email", PASSWORD), 400, "VALIDATION_FAILED");
        ObjectNode extra =
                MAPPER.createObjectNode()
                        .put("newEmail", "nova@example.com")
                        .put("currentPassword", PASSWORD)
                        .put("x", 1);
        expectProblem(send(REQUEST, token, extra), 400, "VALIDATION_FAILED");
        expectProblem(
                send(CONFIRM, null, MAPPER.createObjectNode().put("token", "t").put("x", 1)),
                400,
                "VALIDATION_FAILED");
    }

    /** Outra conta pegou o e-mail entre o pedido e o clique: 409 e o pedido é desfeito. */
    @Test
    void losingTheRaceIs409AndClearsThePendingEmail() throws Exception {
        String token = signup("ana@example.com");
        request(token, "nova@example.com", PASSWORD).andExpect(status().isAccepted());
        String link = lastLink("nova@example.com");
        signup("nova@example.com");
        expectProblem(confirm(link), 409, "EMAIL_ALREADY_REGISTERED");
        assertThat(user("ana@example.com").pendingEmail()).isNull();
    }

    @Test
    void theLogNeverCarriesTheEmailsTheTokenOrThePassword(CapturedOutput output) throws Exception {
        String token = signup("ana.log@example.com");
        request(token, "nova.log@example.com", "senhaErradaLog1")
                .andExpect(status().isBadRequest());
        request(token, "nova.log@example.com", PASSWORD).andExpect(status().isAccepted());
        String link = lastLink("nova.log@example.com");
        confirm(link).andExpect(status().isNoContent());
        confirm(link).andExpect(status().isGone());
        assertThat(output.getAll())
                .doesNotContain("ana.log@example.com")
                .doesNotContain("nova.log@example.com")
                .doesNotContain("senhaErradaLog1")
                .doesNotContain(link)
                .doesNotContain(OpaqueTokens.hash(link))
                .doesNotContain("confirmar-novo-email");
    }

    @Test
    void moreThanOneRequestPerMinuteOrFivePerDayIs429() throws Exception {
        String token = signup("ana.limite@example.com");
        request(token, "n1@example.com", PASSWORD).andExpect(status().isAccepted());
        expectProblem(request(token, "recusado@example.com", PASSWORD), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
        for (int i = 2; i <= 5; i++) {
            this.clock.advance(Duration.ofMinutes(1));
            request(token, "n" + i + "@example.com", PASSWORD).andExpect(status().isAccepted());
        }
        this.clock.advance(Duration.ofMinutes(1));
        expectProblem(request(token, "n6@example.com", PASSWORD), 429, "TOO_MANY_REQUESTS");
        assertThat(mails("recusado@example.com")).isEmpty();
        assertThat(mails("n6@example.com")).isEmpty();
    }

    @Test
    void theOldAddressIsToldWhenTheChangeGoesThrough() throws Exception {
        String token = signup("ana@example.com");
        request(token, "nova@example.com", PASSWORD).andExpect(status().isAccepted());
        confirm(lastLink("nova@example.com")).andExpect(status().isNoContent());
        List<OutboxMail> changed =
                mails("ana@example.com").stream()
                        .filter(mail ->
                                mail.subject().equals("O e-mail da sua conta Fluveny foi trocado"))
                        .toList();
        assertThat(changed).hasSize(1);
        assertThat(changed.getFirst().priority()).isEqualTo(MailPriority.HIGH);
        assertThat(changed.getFirst().text())
                .contains("n***@e***")
                .doesNotContain("nova@example.com")
                .doesNotContain("http");
    }

    @Test
    void aFailureToQueueUndoesThePendingRequest() throws Exception {
        String token = signup("ana@example.com");
        this.accounts.outbox.failInserts = true;
        request(token, "nova@example.com", PASSWORD).andExpect(status().is5xxServerError());
        this.accounts.outbox.failInserts = false;
        assertThat(user("ana@example.com").pendingEmail()).isNull();
        assertThat(this.accounts.tokens.values())
                .noneMatch(t -> t.purpose() == TokenPurpose.EMAIL_CHANGE);
    }
}
