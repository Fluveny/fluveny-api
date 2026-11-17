package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fluveny.fluveny_backend.TestProxySecret;
import com.fluveny.fluveny_backend.platform.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Matriz da spec da história 1.6: o segredo de proxy e o IP do {@code CF-Connecting-IP}. */
class ProxySecretFilterTest {

    private static final String SECRET = TestProxySecret.generate();

    private final Problems problems = mock(Problems.class);

    private final ProxySecretFilter filter = new ProxySecretFilter(SECRET, this.problems);

    private MockFilterChain chain;

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.9.9.9");
        return request;
    }

    private void send(MockHttpServletRequest request) throws Exception {
        this.chain = new MockFilterChain();
        this.filter.doFilter(request, new MockHttpServletResponse(), this.chain);
    }

    private HttpServletRequest passed() {
        return (HttpServletRequest) this.chain.getRequest();
    }

    private void verifyDenied() throws Exception {
        assertThat(this.chain.getRequest()).isNull();
        verify(this.problems)
                .write(
                        any(),
                        any(),
                        argThat(problem ->
                                problem.getStatus() == 403
                                        && "ACCESS_DENIED"
                                                .equals(problem.getProperties().get("code"))));
    }

    @Test
    void throughTheWorkerTheClientIpIsTheCfConnectingIp() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, SECRET);
        request.addHeader(ProxySecretFilter.CLIENT_IP_HEADER, "203.0.113.7");
        send(request);

        assertThat(passed().getRemoteAddr()).isEqualTo("203.0.113.7");
        verify(this.problems, never()).write(any(), any(), any());
    }

    @Test
    void anIpv6ClientIpIsAccepted() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/visitor/attempts");
        request.addHeader(ProxySecretFilter.HEADER, SECRET);
        request.addHeader(ProxySecretFilter.CLIENT_IP_HEADER, " 2001:db8::7 ");
        send(request);

        assertThat(passed().getRemoteAddr()).isEqualTo("2001:db8::7");
    }

    @Test
    void directlyOnTheHostWithoutTheHeaderIsDenied() throws Exception {
        send(request("GET", "/api/v1/units"));
        verifyDenied();
    }

    @Test
    void theHealthCheckPassesWithoutTheHeader() throws Exception {
        send(request("GET", "/api/v1/meta"));
        assertThat(passed().getRemoteAddr()).isEqualTo("10.9.9.9");
        verify(this.problems, never()).write(any(), any(), any());
    }

    @Test
    void onlyGetOfTheHealthCheckIsOpen() throws Exception {
        send(request("POST", "/api/v1/meta"));
        verifyDenied();
    }

    @Test
    void theHealthCheckWithoutTheSecretKeepsTheOriginalIp() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/meta");
        request.addHeader(ProxySecretFilter.CLIENT_IP_HEADER, "203.0.113.7");
        send(request);
        assertThat(passed().getRemoteAddr()).isEqualTo("10.9.9.9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "", " "})
    void aWrongSecretIsDenied(String secret) throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, secret);
        send(request);
        verifyDenied();
    }

    @Test
    void theSecretWithSurroundingWhitespaceIsAccepted() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, " " + SECRET + " ");
        send(request);
        assertThat(passed()).isNotNull();
        verify(this.problems, never()).write(any(), any(), any());
    }

    @Test
    void theSecretWithAnotherCaseIsDenied() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, SECRET.toUpperCase());
        send(request);
        verifyDenied();
    }

    @Test
    void xForwardedForIsNeverRead() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, SECRET);
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        send(request);
        assertThat(passed().getRemoteAddr()).isEqualTo("10.9.9.9");
    }

    @Test
    void aClientIpThatIsNotAnIpIsIgnored() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/units");
        request.addHeader(ProxySecretFilter.HEADER, SECRET);
        request.addHeader(ProxySecretFilter.CLIENT_IP_HEADER, "evil<script>");
        send(request);
        assertThat(passed().getRemoteAddr()).isEqualTo("10.9.9.9");
    }

    @Test
    void theSecretFormatIsValidatedWithoutEchoingIt() {
        assertThat(new ProxySecretProperties(null).validSecret()).isNull();
        assertThat(new ProxySecretProperties("  ").validSecret()).isNull();
        assertThat(new ProxySecretProperties(" " + SECRET + " ").validSecret()).isEqualTo(SECRET);
        String shortSecret = "abc123";
        assertThatThrownBy(() -> new ProxySecretProperties(shortSecret).validSecret())
                .hasMessageContaining("FLUVENY_PROXY_SECRET")
                .message()
                .doesNotContain(shortSecret);
        assertThatThrownBy(() -> new ProxySecretProperties(SECRET + " " + SECRET).validSecret())
                .hasMessageContaining("FLUVENY_PROXY_SECRET");
        assertThatThrownBy(() -> new ProxySecretProperties("x".repeat(257)).validSecret())
                .hasMessageContaining("FLUVENY_PROXY_SECRET");
    }
}
