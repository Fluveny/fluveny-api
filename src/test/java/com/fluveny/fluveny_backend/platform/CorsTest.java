package com.fluveny.fluveny_backend.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * CORS do App web em {@code /api/v1/**} (história 1.22): linhas da matriz da spec. O limite por IP
 * do Visitante fica em 1 por minuto, para o 429 sair na segunda requisição; cada teste usa o seu
 * IP.
 */
@PlatformWebTest
@TestPropertySource(
        properties = {
            "FLUVENY_WEB_ORIGINS=" + CorsTest.ORIGIN + ",https://other.fluveny.test",
            "fluveny.visitor.rate-limit.ip-per-minute=1"
        })
class CorsTest {

    static final String ORIGIN = "https://app.fluveny.test";

    @Autowired private MockMvc mvc;

    private static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static MockHttpServletRequestBuilder preflight(String path, String origin) {
        return options(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-App-Version");
    }

    @Test
    void acceptsThePreflightOfAConfiguredOrigin() throws Exception {
        this.mvc
                .perform(preflight("/api/v1/meta", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                        "GET,POST,PUT,PATCH,DELETE"))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                        containsString("X-App-Version")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600"));
    }

    @Test
    void acceptsEveryConfiguredOriginAndTheDeclaredHeaders() throws Exception {
        this.mvc
                .perform(
                        options("/api/v1/visitor/attempts")
                                .header(HttpHeaders.ORIGIN, "https://other.fluveny.test")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,accept,x-app-version,x-fluveny-client"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "https://other.fluveny.test"));
    }

    @Test
    void refusesThePreflightOfAnotherOrigin() throws Exception {
        this.mvc
                .perform(preflight("/api/v1/meta", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void refusesARequestFromAnotherOrigin() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void refusesAnUndeclaredHeaderOrMethod() throws Exception {
        this.mvc
                .perform(
                        options("/api/v1/meta")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        this.mvc
                .perform(
                        options("/api/v1/meta")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "HEAD"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addsTheHeadersToAnAcceptedGet() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                        containsString("Retry-After")))
                .andExpect(
                        header().stringValues(HttpHeaders.VARY, hasItem(containsString("Origin"))));
    }

    @Test
    void withoutOriginNothingChanges() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void theProblemOfAnInvalidRequestCarriesTheHeaders() throws Exception {
        this.mvc
                .perform(
                        post("/api/v1/visitor/attempts")
                                .with(ip("10.22.0.1"))
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void securityProblemsCarryTheHeaders() throws Exception {
        // 401 de rota com papel, sem credencial e com um Bearer que ninguém reconhece.
        this.mvc
                .perform(
                        post("/api/v1/admin/content/units/x/versions")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
        this.mvc
                .perform(
                        post("/api/v1/admin/content/units/x/versions")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer nope")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void notFoundCarriesTheHeaders() throws Exception {
        this.mvc
                .perform(
                        get("/api/v1/units/does-not-exist-route/x/y")
                                .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void tooManyRequestsExposesRetryAfterAndPreflightsAreNotCounted() throws Exception {
        String address = "10.22.0.2";
        // Preflights respondem no CorsFilter, antes do limite por IP.
        for (int i = 0; i < 3; i++) {
            this.mvc
                    .perform(
                            options("/api/v1/visitor/attempts")
                                    .with(ip(address))
                                    .header(HttpHeaders.ORIGIN, ORIGIN)
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                    .header(
                                            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                            "Content-Type"))
                    .andExpect(status().isOk());
        }
        MockHttpServletRequestBuilder attempt =
                post("/api/v1/visitor/attempts")
                        .with(ip(address))
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}");
        this.mvc.perform(attempt).andExpect(status().isBadRequest());
        this.mvc
                .perform(attempt)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                        containsString("Retry-After")));
    }

    @Test
    void routesOutsideApiV1HaveNoCors() throws Exception {
        this.mvc
                .perform(preflight("/v3/api-docs", ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        this.mvc
                .perform(get("/v3/api-docs").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
