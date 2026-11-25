package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.TestAppUrl;
import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.Passwords;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.auth.infrastructure.ThrottleEntry;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.security.Role;
import jakarta.servlet.http.Cookie;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperação de senha e faixa do e-mail não confirmado (história 2.8): uma linha da matriz por
 * teste, sem Mongo ({@link InMemoryAccounts}), com um relógio que o teste controla e o executor da
 * recuperação síncrono (a tarefa roda antes da resposta, e o teste vê o outbox logo depois).
 *
 * <p>Os limites ficam em memória no bean e sobrevivem entre os testes: cada teste começa num dia
 * próprio (janelas novas de minuto, hora e dia) e com um IP próprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryAccounts.class, PasswordRecoveryTest.Config.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryTest {

    private static final String RECOVERY = "/api/v1/auth/password-recovery";

    private static final String RESET = "/api/v1/auth/password-reset";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String SIGNUP = "/api/v1/auth/signup";

    private static final String ME = "/api/v1/students/me";

    private static final String PASSWORD = "senha123";

    private static final String NEW_PASSWORD = "novaSenha9";

    private static final Instant BASE = Instant.parse("2026-11-23T15:00:00Z");

    private static final Pattern LINK =
            Pattern.compile(
                    Pattern.quote(TestAppUrl.URL + "/redefinir-senha?token=")
                            + "([A-Za-z0-9_-]{43})\\n");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final AtomicInteger RUNS = new AtomicInteger();

    @Autowired private MockMvc mvc;

    @Autowired private InMemoryAccounts accounts;

    @Autowired private UserStore users;

    @Autowired private Passwords passwords;

    @Autowired private MutableClock clock;

    /** O início deste teste: um dia novo, com as janelas dos limites zeradas. */
    private Instant now;

    /** A origem deste teste. */
    private String ip;

    @BeforeEach
    void reset() {
        this.accounts.clear();
        int run = RUNS.incrementAndGet();
        this.now = BASE.plus(Duration.ofDays(run));
        this.clock.set(this.now);
        this.ip = "192.0." + (2 + run / 250) + "." + (1 + run % 250);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE);
        }

        @Bean
        @Primary
        PasswordRecoveryExecutor synchronousRecoveryExecutor() {
            return new PasswordRecoveryExecutor(Runnable::run);
        }
    }

    private String student(String email, AccountStatus status, boolean confirmed) {
        return student(email, status, confirmed, this.now);
    }

    private String student(
            String email, AccountStatus status, boolean confirmed, Instant createdAt) {
        return this.users
                .insert(
                        new User(
                                null,
                                email,
                                this.passwords.hash(PASSWORD),
                                LocalDate.of(1995, 5, 20),
                                Role.STUDENT,
                                status,
                                UUID.randomUUID(),
                                "Ana",
                                null,
                                "0.1",
                                "0.2",
                                createdAt,
                                createdAt,
                                confirmed ? createdAt : null,
                                null,
                                null))
                .orElseThrow();
    }

    private ResultActions recover(String email) throws Exception {
        return recover(email, this.ip);
    }

    private ResultActions recover(String email, String ip) throws Exception {
        return this.mvc.perform(
                post(RECOVERY)
                        .header("X-Fluveny-Client-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.createObjectNode().put("email", email).toString()));
    }

    private ResultActions resetPassword(String token, String password) throws Exception {
        return this.mvc.perform(
                post(RESET)
                        .header("X-Fluveny-Client-IP", this.ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("token", token)
                                        .put("password", password)
                                        .toString()));
    }

    private ResultActions login(String email, String password, String client) throws Exception {
        return this.mvc.perform(
                post(LOGIN)
                        .header("X-Fluveny-Client", client)
                        .header("X-Fluveny-Client-IP", this.ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                MAPPER.createObjectNode()
                                        .put("email", email)
                                        .put("password", password)
                                        .toString()));
    }

    private String bearer(String email) throws Exception {
        String response =
                login(email, PASSWORD, "android")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private String cookie(String email) throws Exception {
        String setCookie =
                login(email, PASSWORD, "web")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    private List<OutboxMail> mails() {
        return this.accounts.outbox.all();
    }

    /** O token do link do último e-mail. */
    private String lastToken() {
        Matcher matcher = LINK.matcher(mails().getLast().text());
        assertThat(matcher.find()).as("link no e-mail").isTrue();
        return matcher.group(1);
    }

    private User user(String id) {
        return this.accounts.users.get(id);
    }

    private User with(User user, String email, AccountStatus status) {
        return new User(
                user.id(),
                email,
                user.passwordHash(),
                user.birthDate(),
                user.role(),
                status,
                user.metricsId(),
                user.name(),
                user.pronounSet(),
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                user.emailVerifiedAt(),
                user.pendingEmail(),
                user.deletionRequestedAt());
    }

    // Pedir.

    @Test
    void aConfirmedStudentGetsOneHighPriorityMailAndAOneHourToken() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);

        recover("ana@example.com").andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(mails()).hasSize(1);
        OutboxMail mail = mails().getFirst();
        assertThat(mail.to()).isEqualTo("ana@example.com");
        assertThat(mail.subject()).isEqualTo("Redefina sua senha do Fluveny");
        assertThat(mail.priority()).isEqualTo(MailPriority.HIGH);
        assertThat(mail.text())
                .startsWith("Olá, Ana!\n")
                .contains("O link vale por 1 hora e só funciona uma vez.")
                .contains("Se você não pediu, ignore este e-mail. Sua senha continua a mesma.")
                .doesNotContain("<");

        String token = lastToken();
        assertThat(this.accounts.tokens).hasSize(1);
        OneTimeToken stored = this.accounts.tokens.values().iterator().next();
        assertThat(stored.tokenHash()).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
        assertThat(stored.userId()).isEqualTo(userId);
        assertThat(stored.purpose()).isEqualTo(TokenPurpose.PASSWORD_RESET);
        assertThat(stored.email()).isEqualTo("ana@example.com");
        assertThat(stored.expiresAt()).isEqualTo(this.now.plus(Duration.ofHours(1)));
    }

    @Test
    void anUnknownEmailIsAcceptedAndNothingIsStored() throws Exception {
        recover("ninguem@example.com")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(mails()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, false", "DEACTIVATED, true", "INVITED, true", "INVITED, false"})
    void anUnconfirmedOrInactiveAccountIsAcceptedAndNothingIsStored(
            AccountStatus status, boolean confirmed) throws Exception {
        student("ana@example.com", status, confirmed);
        recover("ana@example.com").andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(mails()).isEmpty();
    }

    @Test
    void theEmailCaseAndSpacesAreIgnored() throws Exception {
        student("ana@exemplo.test", AccountStatus.ACTIVE, true);
        recover("  ANA@Exemplo.test ").andExpect(status().isAccepted());
        assertThat(mails())
                .singleElement()
                .extracting(OutboxMail::to)
                .isEqualTo("ana@exemplo.test");
    }

    @Test
    void theResponseIsByteForByteTheSameForAKnownAndAnUnknownEmail() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        MockHttpServletResponse known = recover("ana@example.com").andReturn().getResponse();
        MockHttpServletResponse unknown = recover("ninguem@example.com").andReturn().getResponse();

        assertThat(mails()).hasSize(1);
        assertThat(unknown.getStatus()).isEqualTo(known.getStatus()).isEqualTo(202);
        assertThat(unknown.getContentAsByteArray()).isEqualTo(known.getContentAsByteArray());
        assertThat(headers(unknown)).isEqualTo(headers(known));
    }

    private static Map<String, List<String>> headers(MockHttpServletResponse response) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        return headers;
    }

    @Test
    void aNewRequestAfterAMinuteKeepsThePreviousLinkUntilOneIsUsed() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        recover("ana@example.com").andExpect(status().isAccepted());
        String first = lastToken();

        this.clock.advance(Duration.ofMinutes(1));
        recover("ana@example.com").andExpect(status().isAccepted());
        String second = lastToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(mails()).hasSize(2);
        // Um terceiro que saiba o e-mail não invalida o link que o titular já recebeu.
        assertThat(this.accounts.tokens).hasSize(2);
        resetPassword(first, NEW_PASSWORD).andExpect(status().isNoContent());
        // O uso apaga os outros.
        assertThat(this.accounts.tokens).isEmpty();
        expectProblem(resetPassword(second, "outraSenha7"), 410, "PASSWORD_RESET_LINK_INVALID");
    }

    @Test
    void aSecondRequestInTheSameMinuteIsAcceptedAndEnqueuesNothing() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        recover("ana@example.com").andExpect(status().isAccepted());
        recover("ANA@example.com").andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(mails()).hasSize(1);
        assertThat(this.accounts.tokens).hasSize(1);
    }

    @Test
    void theSixthRequestOfTheDayFromAnOriginIsAcceptedAndEnqueuesNothing() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        for (int i = 0; i < 5; i++) {
            recover("ana@example.com").andExpect(status().isAccepted());
            this.clock.advance(Duration.ofMinutes(1));
        }
        assertThat(mails()).hasSize(5);
        recover("ana@example.com").andExpect(status().isAccepted());
        assertThat(mails()).hasSize(5);

        // Outra origem (o titular, enquanto um terceiro esgota a cota da dele) ainda recebe.
        this.clock.advance(Duration.ofMinutes(1));
        recover("ana@example.com", "198.18.1.1").andExpect(status().isAccepted());
        assertThat(mails()).hasSize(6);
    }

    @Test
    void theTwentyFirstRequestOfTheDayForAnEmailIsAcceptedAndEnqueuesNothing() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        for (int origin = 0; origin < 4; origin++) {
            for (int i = 0; i < 5; i++) {
                recover("ana@example.com", "198.18.2." + (origin + 1))
                        .andExpect(status().isAccepted());
                this.clock.advance(Duration.ofMinutes(1));
            }
        }
        assertThat(mails()).hasSize(20);
        recover("ana@example.com", "198.18.2.9").andExpect(status().isAccepted());
        assertThat(mails()).hasSize(20);
    }

    @Test
    void theTwentyFirstRequestOfTheHourFromAnIpIsTooManyRequests() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        for (int i = 0; i < 20; i++) {
            recover("pessoa" + i + "@example.com").andExpect(status().isAccepted());
        }
        String retryAfter =
                expectProblem(recover("ana@example.com"), 429, "TOO_MANY_REQUESTS")
                        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isPositive();
        assertThat(mails()).isEmpty();
        assertThat(this.accounts.tokens).isEmpty();

        // Outra origem segue livre.
        recover("ana@example.com", "198.18.0.1").andExpect(status().isAccepted());
        assertThat(mails()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"email\":\" \"}",
                "{\"email\":\"nao-e-email\"}",
                "{\"email\":\"ana@example.com\",\"name\":\"Ana\"}"
            })
    void anInvalidRequestBodyIs400(String json) throws Exception {
        expectProblem(
                this.mvc.perform(
                        post(RECOVERY)
                                .header("X-Fluveny-Client-IP", this.ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)),
                400,
                "VALIDATION_FAILED");
        assertThat(mails()).isEmpty();
    }

    @Test
    void aFailureInTheTaskStillAnswers202AndLogsWithoutTheEmail(CapturedOutput output)
            throws Exception {
        student("ana.falha@example.com", AccountStatus.ACTIVE, true);
        this.accounts.outbox.failInserts = true;
        recover("ana.falha@example.com")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        assertThat(mails()).isEmpty();
        assertThat(output.getAll())
                .contains("Falha ao emitir a redefinição de senha")
                .doesNotContain("ana.falha@example.com");
    }

    // Redefinir.

    @Test
    void aResetStoresTheNewHashEndsEverySessionAndOpensNone() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String webToken = cookie("ana@example.com");
        String bearer = bearer("ana@example.com");
        assertThat(this.accounts.sessions).hasSize(2);
        // Uma falha antes: a redefinição não zera o login_throttle.
        login("ana@example.com", "errada123", "web").andExpect(status().isUnauthorized());
        Map<String, ThrottleEntry> throttle = new HashMap<>(this.accounts.throttle);
        assertThat(throttle).isNotEmpty();

        recover("ana@example.com").andExpect(status().isAccepted());
        // "á" decomposto (NFD): a senha é gravada em NFC.
        String nfd = Normalizer.normalize("nováSenha9", Normalizer.Form.NFD);
        resetPassword(lastToken(), nfd)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(this.accounts.sessions).isEmpty();
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(this.accounts.throttle).isEqualTo(throttle);
        assertThat(
                        new BCryptPasswordEncoder()
                                .matches(
                                        Normalizer.normalize(nfd, Normalizer.Form.NFC),
                                        user(userId).passwordHash()))
                .isTrue();
        this.mvc
                .perform(get(ME).cookie(new Cookie("fluveny_session", webToken)))
                .andExpect(status().isUnauthorized());
        this.mvc
                .perform(get(ME).header("Authorization", bearer))
                .andExpect(status().isUnauthorized());

        expectProblem(login("ana@example.com", PASSWORD, "web"), 401, "INVALID_CREDENTIALS");
        login("ana@example.com", "nováSenha9", "web").andExpect(status().isOk());
    }

    @Test
    void anotherAccountKeepsItsSessions() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        student("bia@example.com", AccountStatus.ACTIVE, true);
        String bia = bearer("bia@example.com");
        recover("ana@example.com").andExpect(status().isAccepted());
        resetPassword(lastToken(), NEW_PASSWORD).andExpect(status().isNoContent());
        this.mvc.perform(get(ME).header("Authorization", bia)).andExpect(status().isOk());
    }

    @Test
    void aPasswordOutsideTheRuleIs400AndKeepsTheToken() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String hash = user(userId).passwordHash();
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();

        expectProblem(resetPassword(token, "abc"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        assertThat(this.accounts.tokens).hasSize(1);
        assertThat(user(userId).passwordHash()).isEqualTo(hash);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"token\":\"abc\"}",
                "{\"token\":\" \",\"password\":\"novaSenha9\"}",
                "{\"token\":\"abc\",\"password\":\"novaSenha9\",\"email\":\"a@b.test\"}"
            })
    void anInvalidResetBodyIs400(String json) throws Exception {
        expectProblem(
                this.mvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content(json)),
                400,
                "VALIDATION_FAILED");
    }

    @Test
    void aReusedTokenIsGoneAndChangesNothing() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        String hash = user(userId).passwordHash();

        expectProblem(resetPassword(token, "outraSenha7"), 410, "PASSWORD_RESET_LINK_INVALID")
                .andExpect(
                        jsonPath("$.detail")
                                .value("Este link de redefinição não vale mais. Peça outro."));
        assertThat(user(userId).passwordHash()).isEqualTo(hash);
    }

    @Test
    void anUnknownTokenIsGone() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String hash = user(userId).passwordHash();
        recover("ana@example.com").andExpect(status().isAccepted());
        expectProblem(
                resetPassword(OpaqueTokens.generate(), NEW_PASSWORD),
                410,
                "PASSWORD_RESET_LINK_INVALID");
        assertThat(user(userId).passwordHash()).isEqualTo(hash);
        assertThat(this.accounts.tokens).hasSize(1);
    }

    @Test
    void anExpiredTokenIsGone() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String hash = user(userId).passwordHash();
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        this.clock.advance(Duration.ofHours(1));
        expectProblem(resetPassword(token, NEW_PASSWORD), 410, "PASSWORD_RESET_LINK_INVALID");
        assertThat(user(userId).passwordHash()).isEqualTo(hash);
    }

    @Test
    void justBeforeOneHourTheTokenStillWorks() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        this.clock.advance(Duration.ofHours(1).minusSeconds(1));
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void anEmailConfirmationTokenIsNotAResetToken() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String token = OpaqueTokens.generate();
        this.accounts.tokens.put(
                OpaqueTokens.hash(token),
                new OneTimeToken(
                        OpaqueTokens.hash(token),
                        userId,
                        TokenPurpose.EMAIL_CONFIRMATION,
                        "ana@example.com",
                        this.now,
                        this.now.plus(Duration.ofDays(7))));
        expectProblem(resetPassword(token, NEW_PASSWORD), 410, "PASSWORD_RESET_LINK_INVALID");
        assertThat(this.accounts.tokens).hasSize(1);
    }

    @Test
    void aDeactivatedAccountSpendsTheTokenAndKeepsThePassword() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String hash = user(userId).passwordHash();
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        this.accounts.replace(with(user(userId), "ana@example.com", AccountStatus.DEACTIVATED));

        expectProblem(resetPassword(token, NEW_PASSWORD), 410, "PASSWORD_RESET_LINK_INVALID");
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(user(userId).passwordHash()).isEqualTo(hash);
    }

    @Test
    void anEmailChangedAfterTheRequestSpendsTheTokenAndKeepsThePassword() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE, true);
        String hash = user(userId).passwordHash();
        String bearer = bearer("ana@example.com");
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        // A troca de e-mail é da 2.9; aqui, o e-mail da conta muda direto.
        this.accounts.replace(with(user(userId), "nova@example.com", AccountStatus.ACTIVE));

        expectProblem(resetPassword(token, NEW_PASSWORD), 410, "PASSWORD_RESET_LINK_INVALID");
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(user(userId).passwordHash()).isEqualTo(hash);
        // Nada mudou: a sessão segue.
        this.mvc.perform(get(ME).header("Authorization", bearer)).andExpect(status().isOk());
    }

    @Test
    void twoSimultaneousResetsWithTheSameTokenYieldOneNoContentAndOneGone() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true);
        recover("ana@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String password : List.of(NEW_PASSWORD, "outraSenha7")) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return resetPassword(token, password)
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsExactlyInAnyOrder(204, 410);
    }

    // Faixa do e-mail não confirmado.

    @Test
    void sevenDaysWithoutConfirmingRaisesTheBannerInTheProfileAndTheLogin() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, false, this.now.minus(Duration.ofDays(7)));
        login("ana@example.com", PASSWORD, "web")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.emailConfirmed").value(false))
                .andExpect(jsonPath("$.data.student.emailConfirmationOverdue").value(true));
        this.mvc
                .perform(get(ME).header("Authorization", bearer("ana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailConfirmationOverdue").value(true));
    }

    @Test
    void sixDaysWithoutConfirmingHasNoBanner() throws Exception {
        student(
                "ana@example.com",
                AccountStatus.ACTIVE,
                false,
                this.now.minus(Duration.ofDays(7)).plusSeconds(1));
        this.mvc
                .perform(get(ME).header("Authorization", bearer("ana@example.com")))
                .andExpect(jsonPath("$.data.emailConfirmationOverdue").value(false));
    }

    @Test
    void aConfirmedEmailHasNoBanner() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE, true, this.now.minus(Duration.ofDays(30)));
        login("ana@example.com", PASSWORD, "web")
                .andExpect(jsonPath("$.data.student.emailConfirmationOverdue").value(false));
    }

    @Test
    void aSignupHasNoBanner() throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", "ana@example.com")
                        .put("password", PASSWORD)
                        .put("birthDate", "1995-05-20")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        this.mvc
                .perform(
                        post(SIGNUP)
                                .header("X-Fluveny-Client", "web")
                                .header("X-Fluveny-Client-IP", this.ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.student.emailConfirmed").value(false))
                .andExpect(jsonPath("$.data.student.emailConfirmationOverdue").value(false));
    }

    // Log.

    @Test
    void theLogNeverCarriesTheEmailTheTokenOrThePassword(CapturedOutput output) throws Exception {
        student("ana.log@example.com", AccountStatus.ACTIVE, true);
        recover("ana.log@example.com").andExpect(status().isAccepted());
        recover("ninguem.log@example.com").andExpect(status().isAccepted());
        String token = lastToken();
        resetPassword(token, "abc").andExpect(status().isBadRequest());
        resetPassword(token, "senhaDoLog42").andExpect(status().isNoContent());
        resetPassword(token, "senhaDoLog42").andExpect(status().isGone());

        assertThat(output.getAll())
                .doesNotContain("ana.log@example.com")
                .doesNotContain("ninguem.log@example.com")
                .doesNotContain(token)
                .doesNotContain(OpaqueTokens.hash(token))
                .doesNotContain("redefinir-senha")
                .doesNotContain("senhaDoLog42");
    }
}
