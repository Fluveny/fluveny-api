package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cadastro de Aluno adulto, sessão e perfil mínimo (história 2.1): uma linha da matriz por teste,
 * sem Mongo ({@code users}, {@code sessions} e {@code events} em memória, {@link InMemoryAccounts})
 * e com um relógio que o teste controla. O relógio começa em 18/11/2026, 12h de Brasília.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryAccounts.class, SignupTest.ClockConfig.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
class SignupTest {

    private static final String SIGNUP = "/api/v1/auth/signup";

    private static final String ME = "/api/v1/students/me";

    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");

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

    private static ObjectNode body(String email) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", "senha123");
        body.put("birthDate", "1995-05-20");
        body.put("name", "Ana");
        body.put("acceptedTermsVersion", "0.1");
        body.put("acceptedPrivacyVersion", "0.2");
        return body;
    }

    private ResultActions signup(ObjectNode body, String client) throws Exception {
        MockHttpServletRequestBuilder request =
                post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (client != null) {
            request.header("X-Fluveny-Client", client);
        }
        return this.mvc.perform(request);
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** O token do Android, pelo corpo. */
    private String androidToken(String email) throws Exception {
        String response =
                signup(body(email), "android")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    /** O token do web, pelo {@code Set-Cookie}. */
    private String webToken(String email) throws Exception {
        String setCookie =
                signup(body(email), "web")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
    }

    private void assertNothingStored() {
        assertThat(this.accounts.users).isEmpty();
        assertThat(this.accounts.sessions).isEmpty();
        assertThat(this.accounts.events).isEmpty();
        assertThat(this.accounts.tokens).isEmpty();
        assertThat(this.accounts.outbox.all()).isEmpty();
    }

    // Cadastro.

    @Test
    void aWebSignupStoresTheAccountAndTheSessionAndSetsOnlyTheCookie() throws Exception {
        String setCookie =
                signup(body("  Ana.Silva@Example.COM "), "web")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.student.name").value("Ana"))
                        .andExpect(jsonPath("$.data.student.email").value("ana.silva@example.com"))
                        .andExpect(jsonPath("$.data.student.pronounSet").isEmpty())
                        .andExpect(jsonPath("$.data.student.emailConfirmed").value(false))
                        .andExpect(jsonPath("$.data.sessionToken").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .matches("fluveny_session=[A-Za-z0-9_-]{43};.*")
                .contains("Path=/api")
                .contains("Max-Age=2592000")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        User user = this.accounts.users.values().iterator().next();
        assertThat(this.accounts.users).hasSize(1);
        assertThat(user.email()).isEqualTo("ana.silva@example.com");
        assertThat(user.passwordHash()).startsWith("$2").doesNotContain("senha123");
        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.role().name()).isEqualTo("STUDENT");
        assertThat(user.termsVersion()).isEqualTo("0.1");
        assertThat(user.privacyVersion()).isEqualTo("0.2");
        assertThat(user.termsAcceptedAt()).isEqualTo(NOW);
        assertThat(user.emailVerifiedAt()).isNull();
        assertThat(user.pendingEmail()).isNull();
        assertThat(user.deletionRequestedAt()).isNull();

        Session session = this.accounts.sessions.values().iterator().next();
        assertThat(this.accounts.sessions).hasSize(1);
        assertThat(session.userId()).isEqualTo(user.id());
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        String token = setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
        assertThat(session.tokenHash()).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);

        assertThat(this.accounts.events)
                .containsExactly(
                        new Event(
                                EventType.STUDENT_REGISTERED,
                                NOW,
                                null,
                                null,
                                null,
                                null,
                                null,
                                user.metricsId().toString(),
                                false));
    }

    @Test
    void anAndroidSignupReturnsTheTokenOnlyInTheBody() throws Exception {
        signup(body("ana@example.com"), "android")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(
                        jsonPath("$.data.sessionToken")
                                .value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{43}")));
        assertThat(this.accounts.sessions).hasSize(1);
    }

    @Test
    void theResponseNeverCarriesThePasswordOrTheHash() throws Exception {
        String response =
                signup(body("ana@example.com"), "android")
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(response)
                .doesNotContain("senha123")
                .doesNotContain("$2")
                .doesNotContain("passwordHash");
    }

    @Test
    void aRepeatedEmailInAnotherCaseIsAConflictAndStoresNothing() throws Exception {
        signup(body("ana@example.com"), "web").andExpect(status().isCreated());
        this.accounts.events.clear();
        expectProblem(signup(body(" ANA@Example.com"), "web"), 409, "EMAIL_ALREADY_REGISTERED");
        assertThat(this.accounts.users).hasSize(1);
        assertThat(this.accounts.sessions).hasSize(1);
        assertThat(this.accounts.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "12345678", "abc1234", "LONG65"})
    void aWeakPasswordIs400WithTheField(String password) throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("password", password.equals("LONG65") ? "a1" + "b".repeat(63) : password);
        expectProblem(signup(body, "web"), 400, "VALIDATION_FAILED");
        signup(body, "web").andExpect(jsonPath("$.errors[0].field").value("password"));
        assertNothingStored();
    }

    /** 38 caracteres, mas 75 bytes em UTF-8: passa do limite de 72 bytes do BCrypt. */
    @Test
    void aPasswordOverSeventyTwoBytesIs400WithTheField() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("password", "á".repeat(37) + "1");
        expectProblem(signup(body, "web"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        assertNothingStored();
    }

    @Test
    void sixtyFourCharactersAreAccepted() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("password", "a1" + "b".repeat(62));
        signup(body, "web").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"acceptedTermsVersion", "acceptedPrivacyVersion"})
    void withoutTheAcceptanceIs400(String field) throws Exception {
        ObjectNode missing = body("ana@example.com");
        missing.remove(field);
        expectProblem(signup(missing, "web"), 400, "VALIDATION_FAILED");
        ObjectNode empty = body("ana@example.com");
        empty.put(field, " ");
        expectProblem(signup(empty, "web"), 400, "VALIDATION_FAILED");
        ObjectNode tooLong = body("ana@example.com");
        tooLong.put(field, "1".repeat(17));
        expectProblem(signup(tooLong, "web"), 400, "VALIDATION_FAILED");
        assertNothingStored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "birthDate"})
    void withoutARequiredFieldIs400(String field) throws Exception {
        ObjectNode body = body("ana@example.com");
        body.remove(field);
        expectProblem(signup(body, "web"), 400, "VALIDATION_FAILED");
        assertNothingStored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nao-e-email", "ana@", "@example.com"})
    void anInvalidEmailIs400(String email) throws Exception {
        expectProblem(signup(body(email), "web"), 400, "VALIDATION_FAILED");
        assertNothingStored();
    }

    @Test
    void anUnknownFieldIs400() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("role", "ADMIN");
        expectProblem(signup(body, "web"), 400, "VALIDATION_FAILED");
        assertNothingStored();
    }

    // Idade.

    @Test
    void elevenYearsAnd364DaysIsBelowTheMinimum() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", "2014-11-19");
        expectProblem(signup(body, "web"), 422, "AGE_BELOW_MINIMUM");
        assertNothingStored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2014-11-18", "2010-01-01", "2008-11-19"})
    void twelveToSeventeenNeedsTheGuardian(String birthDate) throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", birthDate);
        expectProblem(signup(body, "web"), 422, "GUARDIAN_CONSENT_REQUIRED");
        assertNothingStored();
    }

    @Test
    void eighteenTodayIsAnAdult() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", "2008-11-18");
        signup(body, "web").andExpect(status().isCreated());
    }

    /** 19/11 às 1h UTC ainda é 18/11 em Brasília: o aniversário de 18 anos não chegou. */
    @Test
    void theAgeIsCountedInBrasilia() throws Exception {
        this.clock.set(Instant.parse("2026-11-19T01:00:00Z"));
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", "2008-11-19");
        expectProblem(signup(body, "web"), 422, "GUARDIAN_CONSENT_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-19", "1899-12-31", "2026-13-01"})
    void aFutureOrAncientOrInvalidBirthDateIs400(String birthDate) throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", birthDate);
        expectProblem(signup(body, "web"), 400, "VALIDATION_FAILED");
        assertNothingStored();
    }

    @Test
    void theBirthDateErrorNamesTheField() throws Exception {
        ObjectNode body = body("ana@example.com");
        body.put("birthDate", "2030-01-01");
        signup(body, "web").andExpect(jsonPath("$.errors[0].field").value("birthDate"));
    }

    // Cliente.

    @ParameterizedTest
    @ValueSource(strings = {"", "ios", "WEB "})
    void withoutAKnownClientIs400(String client) throws Exception {
        expectProblem(
                signup(body("ana@example.com"), client.isEmpty() ? null : client),
                400,
                "CLIENT_HEADER_REQUIRED");
        assertNothingStored();
    }

    // Concorrência e evento.

    @Test
    void twoSimultaneousSignupsOfTheSameEmailYieldOneCreatedAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return signup(body("ana@example.com"), "web")
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(this.accounts.users).hasSize(1);
        assertThat(this.accounts.events).hasSize(1);
    }

    @Test
    void aFailureToPublishTheEventKeepsTheSignup() throws Exception {
        this.accounts.failEvents = true;
        signup(body("ana@example.com"), "web").andExpect(status().isCreated());
        assertThat(this.accounts.users).hasSize(1);
        assertThat(this.accounts.sessions).hasSize(1);
    }

    // Perfil e sessão.

    @Test
    void theProfileUpdatesThePronounsWithTheBearer() throws Exception {
        String token = androidToken("ana@example.com");
        this.mvc
                .perform(
                        patch(ME)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"HE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pronounSet").value("HE"));
        this.mvc
                .perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ana"))
                .andExpect(jsonPath("$.data.email").value("ana@example.com"))
                .andExpect(jsonPath("$.data.pronounSet").value("HE"));

        // Nulo volta ao padrão they/them.
        this.mvc
                .perform(
                        patch(ME)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pronounSet").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"{\"pronounSet\":\"IT\"}", "{\"pronounSet\":\"HE\",\"name\":\"x\"}", "{}"})
    void anInvalidPronounUpdateIs400(String json) throws Exception {
        String token = androidToken("ana@example.com");
        expectProblem(
                this.mvc.perform(
                        patch(ME)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)),
                400,
                "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "Bearer nope", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void withoutAKnownSessionTheProfileIs401(String authorization) throws Exception {
        androidToken("ana@example.com");
        MockHttpServletRequestBuilder request = get(ME);
        if (!authorization.isEmpty()) {
            request.header("Authorization", authorization);
        }
        expectProblem(this.mvc.perform(request), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void anUnknownCookieIs401() throws Exception {
        webToken("ana@example.com");
        expectProblem(
                this.mvc.perform(
                        get(ME).cookie(
                                        new Cookie(
                                                "fluveny_session",
                                                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))),
                401,
                "UNAUTHENTICATED");
    }

    @Test
    void anExpiredSessionIs401() throws Exception {
        String token = androidToken("ana@example.com");
        this.clock.advance(Duration.ofDays(30));
        expectProblem(
                this.mvc.perform(get(ME).header("Authorization", "Bearer " + token)),
                401,
                "UNAUTHENTICATED");
    }

    @Test
    void anInactiveAccountIs401() throws Exception {
        String token = androidToken("ana@example.com");
        User user = this.accounts.users.values().iterator().next();
        this.accounts.replace(
                new User(
                        user.id(),
                        user.email(),
                        user.passwordHash(),
                        user.birthDate(),
                        user.role(),
                        AccountStatus.DEACTIVATED,
                        user.metricsId(),
                        user.name(),
                        user.pronounSet(),
                        user.termsVersion(),
                        user.privacyVersion(),
                        user.termsAcceptedAt(),
                        user.createdAt(),
                        user.emailVerifiedAt(),
                        user.pendingEmail(),
                        user.deletionRequestedAt()));
        expectProblem(
                this.mvc.perform(get(ME).header("Authorization", "Bearer " + token)),
                401,
                "UNAUTHENTICATED");
    }

    @Test
    void aCookieWriteWithoutTheClientHeaderIs403AndStoresNothing() throws Exception {
        Cookie cookie = new Cookie("fluveny_session", webToken("ana@example.com"));
        expectProblem(
                this.mvc.perform(
                        patch(ME)
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"HE\"}")),
                403,
                "CLIENT_HEADER_REQUIRED");
        assertThat(this.accounts.users.values().iterator().next().pronounSet()).isNull();

        // Leitura por cookie não precisa do cabeçalho; escrita com ele passa.
        this.mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk());
        this.mvc
                .perform(
                        patch(ME)
                                .cookie(cookie)
                                .header("X-Fluveny-Client", "web")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"SHE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pronounSet").value("SHE"));
    }

    @Test
    void aBearerWriteDoesNotNeedTheClientHeader() throws Exception {
        String token = androidToken("ana@example.com");
        this.mvc
                .perform(
                        patch(ME)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pronounSet\":\"THEY\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theSessionSlidesAfterADayAndTheCookieIsSentAgain() throws Exception {
        Cookie cookie = new Cookie("fluveny_session", webToken("ana@example.com"));

        // Menos de um dia: nada muda.
        this.clock.advance(Duration.ofHours(23));
        this.mvc
                .perform(get(ME).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(this.accounts.sessions.values().iterator().next().expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(30)));

        // Mais de um dia desde a última renovação: agora + 30 dias, e o cookie de novo.
        this.clock.advance(Duration.ofHours(2));
        Instant renewedAt = this.clock.instant();
        String setCookie =
                this.mvc
                        .perform(get(ME).cookie(cookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith("fluveny_session=" + cookie.getValue() + ";")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api");
        assertThat(this.accounts.sessions.values().iterator().next().expiresAt())
                .isEqualTo(renewedAt.plus(Duration.ofDays(30)));

        // Logo depois, não renova de novo.
        this.clock.advance(Duration.ofHours(1));
        this.mvc
                .perform(get(ME).cookie(cookie))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void aBearerSessionSlidesWithoutACookie() throws Exception {
        String token = androidToken("ana@example.com");
        this.clock.advance(Duration.ofDays(2));
        this.mvc
                .perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(this.accounts.sessions.values().iterator().next().expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(32)));
    }
}
