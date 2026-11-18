package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fluveny.fluveny_backend.platform.web.Problems;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Limite por IP de {@code /api/v1/visitor/**} (Decisão 1 da história 1.12) e de {@code
 * /api/v1/auth/**} (história 2.1).
 */
class VisitorRateLimitFilterTest {

    private final Problems problems = mock(Problems.class);

    private final VisitorRateLimitFilter filter =
            new VisitorRateLimitFilter(new RateLimiter(2, Duration.ofMinutes(1)), this.problems);

    private MockHttpServletResponse send(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        this.filter.doFilter(request, response, chain);
        response.setHeader("X-Passed", String.valueOf(chain.getRequest() != null));
        return response;
    }

    @Test
    void refusesAboveTheLimitPerIpWith429AndRetryAfter() throws Exception {
        assertThat(send("/api/v1/visitor/attempts", "10.0.0.1").getHeader("X-Passed"))
                .isEqualTo("true");
        assertThat(send("/api/v1/visitor/completions", "10.0.0.1").getHeader("X-Passed"))
                .isEqualTo("true");
        verify(this.problems, never()).write(any(), any(), any());

        MockHttpServletResponse refused = send("/api/v1/visitor/attempts", "10.0.0.1");
        assertThat(refused.getHeader("X-Passed")).isEqualTo("false");
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, 60L);
        verify(this.problems)
                .write(
                        any(),
                        any(),
                        argThat(problem ->
                                problem.getStatus() == 429
                                        && "TOO_MANY_REQUESTS"
                                                .equals(problem.getProperties().get("code"))));

        // Outro IP tem a própria contagem.
        assertThat(send("/api/v1/visitor/attempts", "10.0.0.2").getHeader("X-Passed"))
                .isEqualTo("true");
    }

    @Test
    void aPercentEncodedVisitorPathIsCounted() throws Exception {
        send("/api/v1/%76isitor/attempts", "10.0.0.4");
        send("/api/v1/%76isitor/attempts", "10.0.0.4");
        MockHttpServletResponse refused = send("/api/v1/%76isitor/attempts", "10.0.0.4");
        assertThat(refused.getHeader("X-Passed")).isEqualTo("false");
        assertThat(refused.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void theSignupIsCountedPerIp() throws Exception {
        assertThat(send("/api/v1/auth/signup", "10.0.0.5").getHeader("X-Passed")).isEqualTo("true");
        assertThat(send("/api/v1/auth/signup", "10.0.0.5").getHeader("X-Passed")).isEqualTo("true");
        MockHttpServletResponse refused = send("/api/v1/auth/signup", "10.0.0.5");
        assertThat(refused.getHeader("X-Passed")).isEqualTo("false");
        assertThat(refused.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void otherRoutesAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("/api/v1/meta", "10.0.0.3").getHeader("X-Passed")).isEqualTo("true");
            assertThat(send("/api/v1/visitorx", "10.0.0.3").getHeader("X-Passed"))
                    .isEqualTo("true");
            assertThat(send("/api/v1/students/me", "10.0.0.3").getHeader("X-Passed"))
                    .isEqualTo("true");
        }
        verify(this.problems, never()).write(any(), any(), any());
    }
}
