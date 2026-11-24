package com.fluveny.fluveny_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.TestAppUrl;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Base dos testes da troca de senha e de e-mail (história 2.9), sem Mongo ({@link
 * InMemoryAccounts}) e com um relógio que o teste controla. O limite de erros da senha atual fica
 * no bean e sobrevive entre testes: cada teste cadastra um Aluno próprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(TestAppUrl.class)
@Import({InMemoryAccounts.class, AccountChangeTestSupport.ClockConfig.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
abstract class AccountChangeTestSupport {

    static final String PASSWORD = "senha123";

    static final Instant NOW = Instant.parse("2026-11-23T15:00:00Z");

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired MockMvc mvc;

    @Autowired InMemoryAccounts accounts;

    @Autowired MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @BeforeEach
    void reset() {
        this.accounts.clear();
        this.clock.set(NOW);
    }

    /** Cadastra pelo Android e devolve o token. */
    String signup(String email) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("birthDate", "1995-05-20");
        body.put("name", "Ana");
        body.put("acceptedTermsVersion", "0.1");
        body.put("acceptedPrivacyVersion", "0.2");
        String response =
                this.mvc
                        .perform(
                                post("/api/v1/auth/signup")
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body.toString()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    /** Entra pelo Android e devolve o token. */
    String login(String email, String password) throws Exception {
        String response =
                this.mvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                MAPPER.createObjectNode()
                                                        .put("email", email)
                                                        .put("password", password)
                                                        .toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return MAPPER.readTree(response).path("data").path("sessionToken").asString();
    }

    ResultActions send(String path, String token, ObjectNode body) throws Exception {
        MockHttpServletRequestBuilder request =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString());
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return this.mvc.perform(request);
    }

    static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }
}
