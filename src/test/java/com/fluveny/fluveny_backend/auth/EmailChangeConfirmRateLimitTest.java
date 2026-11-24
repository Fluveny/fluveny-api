package com.fluveny.fluveny_backend.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** A confirmação da troca de e-mail conta no limite por IP das rotas públicas (história 2.9). */
@TestPropertySource(properties = "fluveny.visitor.rate-limit.ip-per-minute=1")
class EmailChangeConfirmRateLimitTest extends AccountChangeTestSupport {

    @Test
    void theConfirmationIsLimitedPerIp() throws Exception {
        String path = "/api/v1/account/email-change/confirm";
        expectProblem(
                send(path, null, MAPPER.createObjectNode().put("token", "A".repeat(43))),
                410,
                "EMAIL_CHANGE_LINK_INVALID");
        expectProblem(
                        send(path, null, MAPPER.createObjectNode().put("token", "A".repeat(43))),
                        429,
                        "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
    }
}
