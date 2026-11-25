package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.Passwords;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.auth.infrastructure.ThrottleEntry;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.security.Role;
import jakarta.servlet.http.Cookie;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrar e sair (história 2.3): uma linha da matriz por teste, sem Mongo ({@code users}, {@code
 * sessions} e {@code login_throttle} em memória, {@link InMemoryAccounts}) e com um relógio que o
 * teste controla. A origem é o {@code X-Fluveny-Client-IP} que o Worker envia com o segredo de
 * proxy; cada teste usa um IP próprio, para não dividir o limite por IP com os outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryAccounts.class, LoginTest.ClockConfig.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
class LoginTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String LOGOUT = "/api/v1/auth/logout";

    private static final String ME = "/api/v1/students/me";

    private static final String PASSWORD = "senha123";

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired private MockMvc mvc;

    @Autowired private InMemoryAccounts accounts;

    @Autowired private UserStore users;

    @Autowired private Passwords passwords;

    @Autowired private MutableClock clock;

    /** A origem deste teste. */
    private String ip;

    @BeforeEach
    void reset() {
        this.accounts.clear();
        this.clock.set(NOW);
        this.ip = newIp();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    private static String newIp() {
        int n = IPS.incrementAndGet();
        return "198.51." + (100 + n / 250) + "." + (1 + n % 250);
    }

    private String student(String email, AccountStatus status) {
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
                                NOW,
                                NOW,
                                null,
                                null,
                                null))
                .orElseThrow();
    }

    private ResultActions login(String email, String password, String client, String ip)
            throws Exception {
        String body =
                MAPPER.createObjectNode().put("email", email).put("password", password).toString();
        MockHttpServletRequestBuilder request =
                post(LOGIN)
                        .header("X-Fluveny-Client-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);
        if (client != null) {
            request.header("X-Fluveny-Client", client);
        }
        return this.mvc.perform(request);
    }

    private ResultActions login(String email, String password) throws Exception {
        return login(email, password, "web", this.ip);
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String webToken(String email) throws Exception {
        String setCookie =
                login(email, PASSWORD)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
    }

    private String androidToken(String email) throws Exception {
        String response =
                login(email, PASSWORD, "android", this.ip)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    private void fail(String email, String ip) throws Exception {
        expectProblem(login(email, "errada123", "web", ip), 401, "INVALID_CREDENTIALS");
    }

    private ThrottleEntry entryFor(String kind, String value) {
        return this.accounts.throttle.get(
                OpaqueTokens.hash("fluveny-login-throttle\n" + kind + "\n" + value));
    }

    // Entrar.

    @Test
    void aWebLoginStoresTheSessionAndSetsOnlyTheCookie() throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE);
        String setCookie =
                login("ana@example.com", PASSWORD)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.student.name").value("Ana"))
                        .andExpect(jsonPath("$.data.student.email").value("ana@example.com"))
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
        Session session = this.accounts.sessions.values().iterator().next();
        assertThat(this.accounts.sessions).hasSize(1);
        assertThat(session.userId()).isEqualTo(userId);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        String token = setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
        assertThat(session.tokenHash()).isEqualTo(OpaqueTokens.hash(token));
        this.mvc
                .perform(get(ME).cookie(new Cookie("fluveny_session", token)))
                .andExpect(status().isOk());
    }

    @Test
    void anAndroidLoginReturnsTheTokenOnlyInTheBody() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String token = androidToken("ana@example.com");
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        login("ana@example.com", PASSWORD, "android", this.ip)
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        this.mvc
                .perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void theEmailCaseAndSpacesAreIgnored() throws Exception {
        student("ana@exemplo.test", AccountStatus.ACTIVE);
        login("  ANA@Exemplo.test ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theResponseNeverCarriesThePasswordOrTheHash() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String response =
                login("ana@example.com", PASSWORD, "android", this.ip)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(response)
                .doesNotContain(PASSWORD)
                .doesNotContain("$2")
                .doesNotContain("passwordHash");
    }

    @Test
    void aWrongPasswordIs401StoresNoSessionAndCountsTheFailure() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        fail("ana@example.com", this.ip);
        assertThat(this.accounts.sessions).isEmpty();
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(1);
        assertThat(entryFor("account_ip", "ana@example.com\n" + this.ip).failures()).isEqualTo(1);
        assertThat(entryFor("account", "ana@example.com").failures()).isEqualTo(1);
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String wrong =
                expectProblem(login("ana@example.com", "errada123"), 401, "INVALID_CREDENTIALS")
                        .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknown =
                expectProblem(login("bia@example.com", "errada123"), 401, "INVALID_CREDENTIALS")
                        .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(MAPPER.readTree(unknown).path("detail"))
                .isEqualTo(MAPPER.readTree(wrong).path("detail"));
        assertThat(MAPPER.readTree(unknown).path("title"))
                .isEqualTo(MAPPER.readTree(wrong).path("title"));
        assertThat(this.accounts.sessions).isEmpty();
    }

    @Test
    void aDeactivatedAccountWithAWrongPasswordIsLikeAWrongPassword() throws Exception {
        student("ana@example.com", AccountStatus.DEACTIVATED);
        fail("ana@example.com", this.ip);
    }

    @Test
    void aDeactivatedAccountWithTheRightPasswordIs403WithoutASession() throws Exception {
        student("ana@example.com", AccountStatus.DEACTIVATED);
        fail("ana@example.com", this.ip);
        java.util.Map<String, ThrottleEntry> before = java.util.Map.copyOf(this.accounts.throttle);
        expectProblem(login("ana@example.com", PASSWORD), 403, "ACCOUNT_DEACTIVATED");
        assertThat(this.accounts.sessions).isEmpty();
        // A senha conferiu: o 403 não conta falha nem zera os contadores.
        assertThat(this.accounts.throttle).isEqualTo(before);
    }

    @Test
    void anInvitedAccountIsTheGeneric401() throws Exception {
        student("ana@example.com", AccountStatus.INVITED);
        expectProblem(login("ana@example.com", PASSWORD), 401, "INVALID_CREDENTIALS");
        assertThat(this.accounts.sessions).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ios"})
    void withoutAKnownClientIs400(String client) throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        expectProblem(
                login("ana@example.com", PASSWORD, client.isEmpty() ? null : client, this.ip),
                400,
                "CLIENT_HEADER_REQUIRED");
        assertThat(this.accounts.sessions).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"email\":\"ana@example.com\",\"password\":\"senha123\",\"role\":\"ADMIN\"}",
                "{\"email\":\"ana@example.com\"}",
                "{\"password\":\"senha123\"}",
                "{\"email\":\"nao-e-email\",\"password\":\"senha123\"}"
            })
    void anInvalidBodyIs400(String json) throws Exception {
        expectProblem(
                this.mvc.perform(
                        post(LOGIN)
                                .header("X-Fluveny-Client", "web")
                                .header("X-Fluveny-Client-IP", this.ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)),
                400,
                "VALIDATION_FAILED");
        assertThat(this.accounts.throttle).isEmpty();
    }

    // NFC.

    @Test
    void anNfdPasswordEntersAnAccountCreatedInNfc() throws Exception {
        String nfc = Normalizer.normalize("café1234", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize("café1234", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo(nfc);
        this.users.insert(
                new User(
                        null,
                        "ana@example.com",
                        this.passwords.hash(nfc),
                        LocalDate.of(1995, 5, 20),
                        Role.STUDENT,
                        AccountStatus.ACTIVE,
                        UUID.randomUUID(),
                        null,
                        null,
                        "0.1",
                        "0.2",
                        NOW,
                        NOW,
                        null,
                        null,
                        null));
        login("ana@example.com", nfd).andExpect(status().isOk());
        login("ana@example.com", nfc).andExpect(status().isOk());
    }

    /** Conta de antes da normalização: o hash é da forma NFD, como veio. */
    @Test
    void anAccountHashedBeforeNfcStillEntersWithTheSameForm() throws Exception {
        String nfd = Normalizer.normalize("café1234", Normalizer.Form.NFD);
        this.users.insert(
                new User(
                        null,
                        "ana@example.com",
                        new BCryptPasswordEncoder().encode(nfd),
                        LocalDate.of(1995, 5, 20),
                        Role.STUDENT,
                        AccountStatus.ACTIVE,
                        UUID.randomUUID(),
                        null,
                        null,
                        "0.1",
                        "0.2",
                        NOW,
                        NOW,
                        null,
                        null,
                        null));
        login("ana@example.com", nfd).andExpect(status().isOk());
    }

    // Atraso progressivo.

    @Test
    void theFourthAttemptRightAfterThreeFailuresIs429WithoutCheckingThePassword() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        for (int i = 0; i < 3; i++) {
            fail("ana@example.com", this.ip);
        }
        // Senha certa, mas antes do fim da espera: 429, sem conferir nem contar.
        expectProblem(login("ana@example.com", PASSWORD), 429, "LOGIN_THROTTLED")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
        assertThat(this.accounts.sessions).isEmpty();
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(3);
        assertThat(entryFor("account", "ana@example.com").failures()).isEqualTo(3);

        this.clock.advance(Duration.ofSeconds(1));
        login("ana@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void afterTheSeventhFailureTheNextWaitIsSixteenSeconds() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        for (int i = 0; i < 7; i++) {
            fail("ana@example.com", this.ip);
            this.clock.advance(LoginThrottle.wait(i + 1, 3, Duration.ofMinutes(15)));
        }
        // Volta o relógio para logo depois da 7ª falha.
        this.clock.set(entryFor("ip", this.ip).lastFailureAt());
        expectProblem(login("ana@example.com", PASSWORD), 429, "LOGIN_THROTTLED")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "16"));
        this.clock.advance(Duration.ofSeconds(15));
        expectProblem(login("ana@example.com", PASSWORD), 429, "LOGIN_THROTTLED")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
        this.clock.advance(Duration.ofSeconds(1));
        login("ana@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theOwnerFromAnotherOriginWaitsAtMostThirtySeconds() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String attacker = newIp();
        for (int i = 0; i < 10; i++) {
            fail("ana@example.com", attacker);
            this.clock.advance(LoginThrottle.wait(i + 1, 3, Duration.ofMinutes(15)));
        }
        this.clock.set(entryFor("ip", attacker).lastFailureAt());
        String retryAfter =
                expectProblem(login("ana@example.com", PASSWORD), 429, "LOGIN_THROTTLED")
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isPositive().isLessThanOrEqualTo(30);
        this.clock.advance(Duration.ofSeconds(30));
        login("ana@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aSuccessResetsTheAccountCountersButNotTheOrigin() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        for (int i = 0; i < 3; i++) {
            fail("ana@example.com", this.ip);
        }
        this.clock.advance(Duration.ofSeconds(1));
        login("ana@example.com", PASSWORD).andExpect(status().isOk());
        assertThat(entryFor("account", "ana@example.com")).isNull();
        assertThat(entryFor("account_ip", "ana@example.com\n" + this.ip)).isNull();
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(3);
    }

    /** Só o contador de origem: cada tentativa é de uma conta diferente. */
    @Test
    void aForgedXForwardedForCountsTheSameOrigin() throws Exception {
        for (int i = 0; i < 20; i++) {
            expectProblem(
                    this.mvc.perform(
                            post(LOGIN)
                                    .header("X-Fluveny-Client", "web")
                                    .header("X-Fluveny-Client-IP", this.ip)
                                    .header("X-Forwarded-For", "10.0.0." + i)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"email\":\"user"
                                                    + i
                                                    + "@example.com\",\"password\":\"x\"}")),
                    401,
                    "INVALID_CREDENTIALS");
        }
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(20);
        expectProblem(
                this.mvc.perform(
                        post(LOGIN)
                                .header("X-Fluveny-Client", "web")
                                .header("X-Fluveny-Client-IP", this.ip)
                                .header("X-Forwarded-For", "10.9.9.9")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"outro@example.com\",\"password\":\"x\"}")),
                429,
                "LOGIN_THROTTLED");
    }

    @Test
    void theOriginHasTwentyFreeFailures() throws Exception {
        for (int i = 0; i < 19; i++) {
            fail("user" + i + "@example.com", this.ip);
        }
        // A 20ª tentativa, de outra conta, não espera.
        fail("user19@example.com", this.ip);
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(20);
        // A 21ª espera 2^0 = 1 s.
        expectProblem(login("user20@example.com", "errada123"), 429, "LOGIN_THROTTLED")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
    }

    @Test
    void theOriginWaitStopsAtFifteenMinutes() throws Exception {
        for (int i = 0; i < 30; i++) {
            fail("user" + i + "@example.com", this.ip);
            this.clock.advance(LoginThrottle.wait(i + 1, 20, Duration.ofMinutes(15)));
        }
        this.clock.set(entryFor("ip", this.ip).lastFailureAt());
        // 2^(30-20) = 1024 s, no teto de 900, e da própria origem (não o teto de 30 s da conta).
        expectProblem(login("outro@example.com", "errada123"), 429, "LOGIN_THROTTLED")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
    }

    @Test
    void addressesOfTheSameIpv6Slash64CountTogether() throws Exception {
        assertThat(LoginThrottle.origin("2001:db8:1:2::1"))
                .isEqualTo(LoginThrottle.origin("2001:0db8:0001:0002:ffff:0:0:9"))
                .isNotEqualTo(LoginThrottle.origin("2001:db8:1:3::1"));
        for (int i = 0; i < 20; i++) {
            fail("user" + i + "@example.com", "2001:db8:1:2::" + Integer.toHexString(i + 1));
        }
        expectProblem(
                login("outro@example.com", "errada123", "web", "2001:db8:1:2:abcd::7"),
                429,
                "LOGIN_THROTTLED");
        // Outro /64 não espera.
        fail("outro@example.com", "2001:db8:1:3::1");
        // IPv4 continua inteiro.
        assertThat(LoginThrottle.origin("203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void simultaneousWrongLoginsOfTheSameOriginAndAccountGetThree401AndThe429() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        int attempts = 8;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(attempts);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Integer> statuses = new java.util.ArrayList<>();
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results =
                    new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return login("ana@example.com", "errada123")
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).filteredOn(code -> code == 401).hasSize(3);
        assertThat(statuses).filteredOn(code -> code == 429).hasSize(attempts - 3);
        assertThat(entryFor("account", "ana@example.com").failures()).isEqualTo(3);
    }

    @Test
    void aLoginRevokesTheSessionTheRequestAlreadyCarried() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String old = webToken("ana@example.com");
        String setCookie =
                this.mvc
                        .perform(
                                post(LOGIN)
                                        .cookie(new Cookie("fluveny_session", old))
                                        .header("X-Fluveny-Client", "web")
                                        .header("X-Fluveny-Client-IP", this.ip)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"ana@example.com\",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        String fresh = setCookie.substring("fluveny_session=".length(), setCookie.indexOf(';'));
        assertThat(this.accounts.sessions)
                .containsOnlyKeys(OpaqueTokens.hash(fresh))
                .doesNotContainKey(OpaqueTokens.hash(old));
        expectProblem(
                this.mvc.perform(get(ME).cookie(new Cookie("fluveny_session", old))),
                401,
                "UNAUTHENTICATED");

        // Android: o Bearer antigo também sai.
        String bearer = androidToken("ana@example.com");
        this.mvc
                .perform(
                        post(LOGIN)
                                .header("Authorization", "Bearer " + bearer)
                                .header("X-Fluveny-Client", "android")
                                .header("X-Fluveny-Client-IP", this.ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"ana@example.com\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andExpect(status().isOk());
        assertThat(this.accounts.sessions).doesNotContainKey(OpaqueTokens.hash(bearer));
    }

    @Test
    void anExpiredCounterStartsOver() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        for (int i = 0; i < 3; i++) {
            fail("ana@example.com", this.ip);
        }
        this.clock.advance(Duration.ofHours(1));
        fail("ana@example.com", this.ip);
        assertThat(entryFor("ip", this.ip).failures()).isEqualTo(1);
        assertThat(entryFor("ip", this.ip).expiresAt())
                .isEqualTo(this.clock.instant().plus(Duration.ofHours(1)));
    }

    @Test
    void theThrottleKeysCarryNoEmailNorIp() throws Exception {
        fail("ana@example.com", this.ip);
        assertThat(this.accounts.throttle).hasSize(3);
        for (ThrottleEntry entry : this.accounts.throttle.values()) {
            assertThat(entry.keyHash())
                    .matches("[0-9a-f]{64}")
                    .doesNotContain("ana")
                    .doesNotContain(this.ip);
        }
    }

    // Sair.

    @Test
    void aWebLogoutDeletesTheSessionAndTheCookie() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        Cookie cookie = new Cookie("fluveny_session", webToken("ana@example.com"));
        String setCookie =
                this.mvc
                        .perform(post(LOGOUT).cookie(cookie).header("X-Fluveny-Client", "web"))
                        .andExpect(status().isNoContent())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith("fluveny_session=;")
                .contains("Max-Age=0")
                .contains("Path=/api")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
        assertThat(this.accounts.sessions).isEmpty();
        expectProblem(this.mvc.perform(get(ME).cookie(cookie)), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    /** Sessão renovada na própria requisição: o cookie que sai é o que apaga, não o renovado. */
    @Test
    void aLogoutOfASlidingSessionStillClearsTheCookie() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        Cookie cookie = new Cookie("fluveny_session", webToken("ana@example.com"));
        this.clock.advance(Duration.ofDays(2));
        var response =
                this.mvc
                        .perform(post(LOGOUT).cookie(cookie).header("X-Fluveny-Client", "web"))
                        .andExpect(status().isNoContent())
                        .andReturn()
                        .getResponse();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .singleElement()
                .asString()
                .contains("Max-Age=0");
        assertThat(this.accounts.sessions).isEmpty();
    }

    @Test
    void anAndroidLogoutDeletesTheSession() throws Exception {
        student("ana@example.com", AccountStatus.ACTIVE);
        String bearer = "Bearer " + androidToken("ana@example.com");
        this.mvc
                .perform(
                        post(LOGOUT)
                                .header("Authorization", bearer)
                                .header("X-Fluveny-Client", "android"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(this.accounts.sessions).isEmpty();
        expectProblem(
                this.mvc.perform(get(ME).header("Authorization", bearer)), 401, "UNAUTHENTICATED");
    }

    @Test
    void aLogoutWithoutASessionIs204() throws Exception {
        this.mvc
                .perform(post(LOGOUT).header("X-Fluveny-Client", "web"))
                .andExpect(status().isNoContent());
        this.mvc
                .perform(
                        post(LOGOUT)
                                .header("X-Fluveny-Client", "android")
                                .header(
                                        "Authorization",
                                        "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNoContent());
    }

    @Test
    void aLogoutWithoutTheClientHeaderIs400() throws Exception {
        expectProblem(this.mvc.perform(post(LOGOUT)), 400, "CLIENT_HEADER_REQUIRED");
    }

    // Conta fora de ACTIVE.

    @ParameterizedTest
    @EnumSource(
            value = AccountStatus.class,
            names = {"DEACTIVATED", "INVITED"})
    void aSessionOfAnInactiveAccountIs401OnWebAndAndroid(AccountStatus status) throws Exception {
        String userId = student("ana@example.com", AccountStatus.ACTIVE);
        String cookieToken = webToken("ana@example.com");
        String bearerToken = androidToken("ana@example.com");
        User user = this.accounts.users.get(userId);
        this.accounts.replace(
                new User(
                        user.id(),
                        user.email(),
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
                        user.deletionRequestedAt()));
        expectProblem(
                        this.mvc.perform(
                                get(ME).cookie(new Cookie("fluveny_session", cookieToken))),
                        401,
                        "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        expectProblem(
                        this.mvc.perform(get(ME).header("Authorization", "Bearer " + bearerToken)),
                        401,
                        "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }
}
