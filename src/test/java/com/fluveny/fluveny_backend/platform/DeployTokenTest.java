package com.fluveny.fluveny_backend.platform;

import static com.fluveny.fluveny_backend.platform.ProblemFormatTest.expectProblem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Token de deploy (Decisão 2 da história 1.7): o Bearer certo autentica o principal de máquina com
 * {@code Role.DEPLOY}, fora da hierarquia; sem token é 401, com token errado é 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PlatformTestRoutes.class)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
public class DeployTokenTest {

    /** Gerado a cada execução: nenhum token fica no repositório. */
    static final String TOKEN = UUID.randomUUID() + "-" + UUID.randomUUID();

    @DynamicPropertySource
    static void tokenHash(DynamicPropertyRegistry registry) {
        registry.add("FLUVENY_DEPLOY_TOKEN_SHA256", () -> sha256Hex(TOKEN).toUpperCase());
    }

    /** O sha256 do token em hex, como o {@code FLUVENY_DEPLOY_TOKEN_SHA256}. */
    public static String sha256Hex(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Autowired private MockMvc mvc;

    @Test
    void theRightTokenReachesDeployRoutes() throws Exception {
        this.mvc
                .perform(get("/test/roles/deploy").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deploy"));
    }

    @Test
    void withoutTokenIsUnauthenticated() throws Exception {
        expectProblem(this.mvc.perform(get("/test/roles/deploy")), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void aWrongTokenIsForbidden() throws Exception {
        expectProblem(
                this.mvc.perform(
                        get("/test/roles/deploy").header("Authorization", "Bearer " + TOKEN + "x")),
                403,
                "ACCESS_DENIED");
        expectProblem(
                this.mvc.perform(get("/test/roles/deploy").header("Authorization", "Bearer ")),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void theDeployPrincipalIsOutsideTheHierarchy() throws Exception {
        String bearer = "Bearer " + TOKEN;
        expectProblem(
                this.mvc.perform(get("/test/roles/admin").header("Authorization", bearer)),
                403,
                "ACCESS_DENIED");
        expectProblem(
                this.mvc.perform(get("/test/roles/creator").header("Authorization", bearer)),
                403,
                "ACCESS_DENIED");
        expectProblem(
                this.mvc.perform(get("/test/roles/deploy").with(user("a").roles("ADMIN"))),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void publicRoutesIgnoreAnyBearer() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta").header("Authorization", "Bearer qualquer"))
                .andExpect(status().isOk());
    }
}
