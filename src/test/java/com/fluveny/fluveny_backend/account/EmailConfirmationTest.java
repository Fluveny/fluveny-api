package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.TestAppUrl;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.auth.InMemoryAccounts;
import com.fluveny.fluveny_backend.auth.MutableClock;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.infrastructure.MailStatus;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Confirmação de e-mail (história 2.2): uma linha da matriz por teste, sem Mongo ({@code users},
 * {@code one_time_tokens} e {@code mail_outbox} em memória, {@link InMemoryAccounts}) e com um
 * relógio que o teste controla. O despacho agendado fica desligado; o envio é do {@code
 * MailDispatcherTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryAccounts.class, EmailConfirmationTest.ClockConfig.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
@ExtendWith(OutputCaptureExtension.class)
class EmailConfirmationTest {

    private static final String SIGNUP = "/api/v1/auth/signup";

    private static final String CONFIRM = "/api/v1/account/email-confirmation";

    private static final String RESEND = "/api/v1/students/me/email-confirmation";

    private static final String ME = "/api/v1/students/me";

    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    private static final Pattern LINK =
            Pattern.compile(
                    Pattern.quote(TestAppUrl.URL + "/confirmar-email?token=")
                            + "([A-Za-z0-9_-]{43})\\n");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private InMemoryAccounts accounts;

    @Autowired private MutableClock clock;

    @BeforeEach
    void reset() {
        this.accounts.clear();
        this.clock.set(NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    /** Cadastra pelo Android e devolve o Bearer. */
    private String signup(String email, String name) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", "senha123");
        body.put("birthDate", "1995-05-20");
        if (name != null) {
            body.put("name", name);
        }
        body.put("acceptedTermsVersion", "0.1");
        body.put("acceptedPrivacyVersion", "0.2");
        String response =
                this.mvc
                        .perform(
                                post(SIGNUP)
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body.toString()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private ResultActions confirm(String token) throws Exception {
        return this.mvc.perform(
                post(CONFIRM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("token", token).toString()));
    }

    private ResultActions resend(String bearer) throws Exception {
        return this.mvc.perform(post(RESEND).header("Authorization", bearer));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** O token do link do último e-mail. */
    private String lastToken() {
        List<OutboxMail> mails = this.accounts.outbox.all();
        Matcher matcher = LINK.matcher(mails.getLast().text());
        assertThat(matcher.find()).as("link no e-mail").isTrue();
        return matcher.group(1);
    }

    private User onlyUser() {
        assertThat(this.accounts.users).hasSize(1);
        return this.accounts.users.values().iterator().next();
    }

    // Cadastro.

    @Test
    void aSignupStoresOneTokenAndEnqueuesOneNormalPendingMail() throws Exception {
        signup("ana@example.com", "Ana");

        List<OutboxMail> mails = this.accounts.outbox.all();
        assertThat(mails).hasSize(1);
        OutboxMail mail = mails.getFirst();
        assertThat(mail.to()).isEqualTo("ana@example.com");
        assertThat(mail.subject()).isEqualTo("Confirme seu e-mail no Fluveny");
        assertThat(mail.priority()).isEqualTo(MailPriority.NORMAL);
        assertThat(mail.status()).isEqualTo(MailStatus.PENDING);
        assertThat(mail.attempts()).isZero();
        assertThat(mail.nextAttemptAt()).isEqualTo(NOW);
        assertThat(mail.createdAt()).isEqualTo(NOW);
        assertThat(mail.sentAt()).isNull();
        // Sem SMTP, o PENDING não fica para sempre.
        assertThat(mail.purgeAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(mail.text())
                .startsWith("Olá, Ana!\n")
                .contains("O link vale por 7 dias.")
                .contains("Se você não criou uma conta, ignore este e-mail.")
                .doesNotContain("<");

        String token = lastToken();
        assertThat(this.accounts.tokens).hasSize(1);
        OneTimeToken stored = this.accounts.tokens.values().iterator().next();
        assertThat(stored.tokenHash()).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
        assertThat(stored.userId()).isEqualTo(onlyUser().id());
        assertThat(stored.purpose()).isEqualTo(TokenPurpose.EMAIL_CONFIRMATION);
        assertThat(stored.email()).isEqualTo("ana@example.com");
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void withoutANameTheGreetingIsJustOla() throws Exception {
        signup("ana@example.com", null);
        assertThat(this.accounts.outbox.all().getFirst().text()).startsWith("Olá!\n");
    }

    @Test
    void aFailureToEnqueueKeepsTheSignup() throws Exception {
        this.accounts.outbox.failInserts = true;
        String bearer = signup("ana@example.com", "Ana");
        assertThat(this.accounts.users).hasSize(1);
        assertThat(this.accounts.sessions).hasSize(1);
        assertThat(this.accounts.outbox.all()).isEmpty();

        // O Aluno pede outro pelo reenvio.
        this.accounts.outbox.failInserts = false;
        resend(bearer).andExpect(status().isAccepted());
        assertThat(this.accounts.outbox.all()).hasSize(1);
        assertThat(this.accounts.tokens).hasSize(1);
    }

    // Confirmação.

    @Test
    void theLinkTokenConfirmsTheEmailAndIsDeleted() throws Exception {
        String bearer = signup("ana@example.com", "Ana");
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(jsonPath("$.data.emailConfirmed").value(false));

        this.clock.advance(Duration.ofHours(1));
        confirm(lastToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("E-mail confirmado."));

        assertThat(onlyUser().emailVerifiedAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(this.accounts.tokens).isEmpty();
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailConfirmed").value(true));
    }

    @Test
    void aReusedTokenIsGoneAndChangesNothing() throws Exception {
        signup("ana@example.com", "Ana");
        String token = lastToken();
        confirm(token).andExpect(status().isOk());
        Instant verifiedAt = onlyUser().emailVerifiedAt();

        this.clock.advance(Duration.ofMinutes(5));
        expectProblem(confirm(token), 410, "CONFIRMATION_LINK_INVALID");
        assertThat(onlyUser().emailVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void anUnknownTokenIsGone() throws Exception {
        signup("ana@example.com", "Ana");
        expectProblem(confirm(OpaqueTokens.generate()), 410, "CONFIRMATION_LINK_INVALID");
        assertThat(onlyUser().emailVerifiedAt()).isNull();
        assertThat(this.accounts.tokens).hasSize(1);
    }

    @Test
    void anExpiredTokenIsGone() throws Exception {
        signup("ana@example.com", "Ana");
        String token = lastToken();
        this.clock.advance(Duration.ofDays(7));
        expectProblem(confirm(token), 410, "CONFIRMATION_LINK_INVALID");
        assertThat(onlyUser().emailVerifiedAt()).isNull();
    }

    @Test
    void justBeforeSevenDaysTheTokenStillWorks() throws Exception {
        signup("ana@example.com", "Ana");
        String token = lastToken();
        this.clock.advance(Duration.ofDays(7).minusSeconds(1));
        confirm(token).andExpect(status().isOk());
    }

    @Test
    void aTokenForAnotherEmailIsGoneAndConsumedWithoutConfirming() throws Exception {
        signup("ana@example.com", "Ana");
        String token = lastToken();
        User user = onlyUser();
        // A troca de e-mail é da 2.9; aqui, o e-mail da conta muda direto.
        this.accounts.replace(
                new User(
                        user.id(),
                        "nova@example.com",
                        user.passwordHash(),
                        user.birthDate(),
                        user.role(),
                        user.status(),
                        user.metricsId(),
                        user.name(),
                        user.pronounSet(),
                        user.termsVersion(),
                        user.privacyVersion(),
                        user.termsAcceptedAt(),
                        user.createdAt(),
                        null,
                        null,
                        null));
        expectProblem(confirm(token), 410, "CONFIRMATION_LINK_INVALID");
        assertThat(onlyUser().emailVerifiedAt()).isNull();
        assertThat(this.accounts.tokens).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"token\":\" \"}", "{\"token\":\"abc\",\"userId\":\"x\"}"})
    void anInvalidBodyIs400(String json) throws Exception {
        expectProblem(
                this.mvc.perform(
                        post(CONFIRM).contentType(MediaType.APPLICATION_JSON).content(json)),
                400,
                "VALIDATION_FAILED");
    }

    @Test
    void twoSimultaneousConfirmationsYieldOneOkAndOneGone() throws Exception {
        signup("ana@example.com", "Ana");
        String token = lastToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
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
        assertThat(statuses).containsExactlyInAnyOrder(200, 410);
        assertThat(onlyUser().emailVerifiedAt()).isNotNull();
    }

    // Reenvio.

    @Test
    void aResendInvalidatesThePreviousLinkAndEnqueuesAnother() throws Exception {
        String bearer = signup("ana@example.com", "Ana");
        String first = lastToken();

        this.clock.advance(Duration.ofMinutes(2));
        resend(bearer).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(this.accounts.outbox.all()).hasSize(2);
        assertThat(this.accounts.tokens).hasSize(1);
        String second = lastToken();
        assertThat(second).isNotEqualTo(first);
        expectProblem(confirm(first), 410, "CONFIRMATION_LINK_INVALID");
        confirm(second).andExpect(status().isOk());
        assertThat(onlyUser().emailVerifiedAt()).isNotNull();
    }

    @Test
    void aResendWhenConfirmedIsAConflictAndEnqueuesNothing() throws Exception {
        String bearer = signup("ana@example.com", "Ana");
        confirm(lastToken()).andExpect(status().isOk());
        expectProblem(resend(bearer), 409, "EMAIL_ALREADY_CONFIRMED");
        assertThat(this.accounts.outbox.all()).hasSize(1);
        assertThat(this.accounts.tokens).isEmpty();
    }

    @Test
    void aSecondResendInTheSameMinuteIsTooManyRequests() throws Exception {
        String bearer = signup("ana@example.com", "Ana");
        resend(bearer).andExpect(status().isAccepted());
        expectProblem(resend(bearer), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertThat(this.accounts.outbox.all()).hasSize(2);
    }

    @Test
    void theSixthResendOfTheDayIsTooManyRequests() throws Exception {
        String bearer = signup("ana@example.com", "Ana");
        for (int i = 0; i < 5; i++) {
            resend(bearer).andExpect(status().isAccepted());
            this.clock.advance(Duration.ofMinutes(1));
        }
        String retryAfter =
                expectProblem(resend(bearer), 429, "TOO_MANY_REQUESTS")
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isPositive();
        assertThat(this.accounts.outbox.all()).hasSize(6);
    }

    @Test
    void anotherStudentHasItsOwnLimit() throws Exception {
        String ana = signup("ana@example.com", "Ana");
        String bia = signup("bia@example.com", "Bia");
        resend(ana).andExpect(status().isAccepted());
        resend(bia).andExpect(status().isAccepted());
    }

    @Test
    void withoutASessionTheResendIs401() throws Exception {
        expectProblem(this.mvc.perform(post(RESEND)), 401, "UNAUTHENTICATED");
        assertThat(this.accounts.outbox.all()).isEmpty();
    }

    // Log.

    @Test
    void theLogNeverCarriesTheTokenTheLinkOrTheEmail(CapturedOutput output) throws Exception {
        this.accounts.failEvents = true;
        String bearer = signup("ana.log@example.com", "Ana");
        String first = lastToken();
        this.accounts.outbox.failInserts = true;
        this.clock.advance(Duration.ofMinutes(2));
        resend(bearer);
        this.accounts.outbox.failInserts = false;
        this.clock.advance(Duration.ofMinutes(2));
        resend(bearer).andExpect(status().isAccepted());
        String second = lastToken();
        confirm(first);
        confirm(second).andExpect(status().isOk());

        assertThat(output.getAll())
                .doesNotContain(first)
                .doesNotContain(second)
                .doesNotContain(OpaqueTokens.hash(second))
                .doesNotContain("confirmar-email")
                .doesNotContain("ana.log@example.com");
    }
}
