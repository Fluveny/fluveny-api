package com.fluveny.fluveny_backend.platform;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.TestProxySecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * O segredo de proxy na cadeia inteira do perfil {@code prod} (história 1.6). O MockMvc daqui é
 * montado sem o cabeçalho padrão da {@link TestProxySecret}, para provar a recusa. O limite por IP
 * do Visitante fica em 1 por minuto, para o 429 mostrar qual IP foi contado.
 */
@PlatformWebTest
@TestPropertySource(properties = "fluveny.visitor.rate-limit.ip-per-minute=1")
class ProxySecretTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void bareMockMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder attempt(String remoteAddr) {
        return post("/api/v1/visitor/attempts")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    @Test
    void withoutTheSecretTheApiIsDenied() throws Exception {
        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(status().isForbidden())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_TYPE,
                                        startsWith("application/problem+json")))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        this.mvc
                .perform(get("/v3/api-docs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void theWrongSecretIsDeniedBeforeCors() throws Exception {
        this.mvc
                .perform(
                        get("/api/v1/units")
                                .header(TestProxySecret.HEADER, "x")
                                .header(HttpHeaders.ORIGIN, "https://app.fluveny.test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void theHealthCheckIsOpen() throws Exception {
        this.mvc.perform(get("/api/v1/meta")).andExpect(status().isOk());
    }

    @Test
    void theRateLimitCountsTheFluvenyClientIp() throws Exception {
        // Mesmo remoteAddr (o do Worker), clientes diferentes: cada um tem a própria contagem.
        String worker = "10.33.0.1";
        this.mvc
                .perform(
                        attempt(worker)
                                .header(TestProxySecret.HEADER, TestProxySecret.value())
                                .header("X-Fluveny-Client-IP", "203.0.113.10"))
                .andExpect(status().isBadRequest());
        this.mvc
                .perform(
                        attempt(worker)
                                .header(TestProxySecret.HEADER, TestProxySecret.value())
                                .header("X-Fluveny-Client-IP", "203.0.113.11"))
                .andExpect(status().isBadRequest());
        this.mvc
                .perform(
                        attempt(worker)
                                .header(TestProxySecret.HEADER, TestProxySecret.value())
                                .header("X-Fluveny-Client-IP", "203.0.113.10"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aForgedXForwardedForDoesNotChangeTheCountedIp() throws Exception {
        String worker = "10.33.0.2";
        this.mvc
                .perform(
                        attempt(worker)
                                .header(TestProxySecret.HEADER, TestProxySecret.value())
                                .header("X-Forwarded-For", "1.2.3.4"))
                .andExpect(status().isBadRequest());
        this.mvc
                .perform(
                        attempt(worker)
                                .header(TestProxySecret.HEADER, TestProxySecret.value())
                                .header("X-Forwarded-For", "5.6.7.8"))
                .andExpect(status().isTooManyRequests());
    }
}
