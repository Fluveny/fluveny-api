package com.fluveny.fluveny_backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** O token da requisição (história 2.9): Bearer em branco conta como ausente. */
class RequestTokensTest {

    @Test
    void aBlankBearerFallsBackToTheCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer   ");
        request.setCookies(new Cookie("fluveny_session", "do-cookie"));
        assertThat(RequestTokens.bearer(request)).isNull();
        assertThat(RequestTokens.current(request)).isEqualTo("do-cookie");
    }

    @Test
    void theBearerWins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer do-bearer");
        request.setCookies(new Cookie("fluveny_session", "do-cookie"));
        assertThat(RequestTokens.current(request)).isEqualTo("do-bearer");
    }
}
