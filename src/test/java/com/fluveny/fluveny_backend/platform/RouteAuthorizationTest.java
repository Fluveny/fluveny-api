package com.fluveny.fluveny_backend.platform;

import static com.fluveny.fluveny_backend.platform.ProblemFormatTest.expectProblem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.RegisteredStudent;
import com.fluveny.fluveny_backend.auth.Sessions;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deny-by-default e hierarquia de papéis (AD-12): linhas da matriz da história 1.2, e a sessão de
 * verdade do Aluno (história 2.1).
 */
@PlatformWebTest
class RouteAuthorizationTest {

    @Autowired private MockMvc mvc;

    @Autowired private RegisterStudent register;

    @Autowired private Sessions sessions;

    @Test
    void metaIsPublic() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minAppVersion").value("0.0.0"))
                // Os Tipos do learning, com sinceAppVersion nulo até a tag do App (1.11, 1.13,
                // 1.14, 1.15, 1.16, 1.17, 1.18, 1.19).
                .andExpect(jsonPath("$.data.activityTypes.length()").value(9))
                .andExpect(jsonPath("$.data.activityTypes[0].type").value("choice"))
                .andExpect(jsonPath("$.data.activityTypes[0].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[1].type").value("dialogue"))
                .andExpect(jsonPath("$.data.activityTypes[1].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[2].type").value("cards"))
                .andExpect(jsonPath("$.data.activityTypes[2].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[3].type").value("expression"))
                .andExpect(jsonPath("$.data.activityTypes[3].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[4].type").value("speech"))
                .andExpect(jsonPath("$.data.activityTypes[4].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[5].type").value("match"))
                .andExpect(jsonPath("$.data.activityTypes[5].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[6].type").value("gaps"))
                .andExpect(jsonPath("$.data.activityTypes[6].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[7].type").value("free"))
                .andExpect(jsonPath("$.data.activityTypes[7].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.activityTypes[8].type").value("grammar"))
                .andExpect(jsonPath("$.data.activityTypes[8].sinceAppVersion").value(nullValue()))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        this.mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void undeclaredRouteIsUnauthorizedForAnonymous() throws Exception {
        expectProblem(this.mvc.perform(get("/test/undeclared")), 401, "ACCESS_DENIED")
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void undeclaredRouteIsForbiddenEvenForAdmin() throws Exception {
        expectProblem(
                        this.mvc.perform(get("/test/undeclared").with(user("a").roles("ADMIN"))),
                        403,
                        "ACCESS_DENIED")
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void roleRouteIsUnauthenticatedForAnonymous() throws Exception {
        expectProblem(this.mvc.perform(get("/test/roles/admin")), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/test/conflicting", "/test/empty-roles", "/test/router"})
    void invalidDeclarationsAndNonMethodHandlersAreDenied(String path) throws Exception {
        expectProblem(this.mvc.perform(get(path)), 401, "ACCESS_DENIED");
        expectProblem(
                this.mvc.perform(get(path).with(user("a").roles("ADMIN"))), 403, "ACCESS_DENIED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config"})
    void apiDocsPathsArePublic(String path) throws Exception {
        this.mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void adminReachesContentCreatorRoutes() throws Exception {
        this.mvc
                .perform(get("/test/roles/creator").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("creator"));
    }

    @Test
    void contentCreatorCannotReachAdminRoutes() throws Exception {
        expectProblem(
                this.mvc.perform(get("/test/roles/admin").with(user("c").roles("CONTENT_CREATOR"))),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void studentCannotReachAdminRoutes() throws Exception {
        expectProblem(
                this.mvc.perform(get("/test/roles/admin").with(user("s").roles("STUDENT"))),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void studentIsOutsideTheHierarchy() throws Exception {
        expectProblem(
                this.mvc.perform(get("/test/roles/student").with(user("a").roles("ADMIN"))),
                403,
                "ACCESS_DENIED");
        this.mvc
                .perform(get("/test/roles/student").with(user("s").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    /**
     * Sem {@code FLUVENY_DEPLOY_TOKEN_SHA256}, nenhum token entra (história 1.7), e o Bearer que
     * ninguém reconhece é 401 (história 2.1).
     */
    @Test
    void withoutTheDeployHashEveryTokenIsDenied() throws Exception {
        expectProblem(
                        this.mvc.perform(
                                get("/test/roles/deploy")
                                        .header("Authorization", "Bearer qualquer-token")),
                        401,
                        "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        expectProblem(this.mvc.perform(get("/test/roles/deploy")), 401, "UNAUTHENTICATED");
    }

    /** A sessão de verdade (história 2.1): o Aluno do cadastro, pelo Bearer e pelo cookie. */
    @Test
    void aRealStudentSessionReachesOnlyStudentRoutes() throws Exception {
        RegisteredStudent student =
                this.register.register(
                        new NewStudent(
                                UUID.randomUUID() + "@example.com",
                                "senha123",
                                LocalDate.of(1995, 5, 20),
                                null,
                                "0.1",
                                "0.2"));
        String token = this.sessions.create(student.userId(), student.role()).token();
        String bearer = "Bearer " + token;

        this.mvc
                .perform(get("/test/roles/student").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("student"));
        this.mvc
                .perform(get("/test/roles/student").cookie(new Cookie("fluveny_session", token)))
                .andExpect(status().isOk());
        for (String path :
                new String[] {"/test/roles/admin", "/test/roles/creator", "/test/roles/deploy"}) {
            expectProblem(
                    this.mvc.perform(get(path).header("Authorization", bearer)),
                    403,
                    "ACCESS_DENIED");
        }
        expectProblem(
                this.mvc.perform(get("/test/undeclared").header("Authorization", bearer)),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void methodDeclarationWinsOverTheClass() throws Exception {
        expectProblem(this.mvc.perform(get("/test/method-wins")), 401, "UNAUTHENTICATED");
        this.mvc
                .perform(get("/test/method-wins").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
