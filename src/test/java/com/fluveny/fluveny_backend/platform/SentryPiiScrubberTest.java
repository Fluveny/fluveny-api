package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.platform.observability.SentryPiiScrubber;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** O {@code beforeSend} tira toda PII de um evento sintético (NFR17). */
class SentryPiiScrubberTest {

    @Test
    void removesUserIpCookiesAuthHeadersAndBody() {
        SentryEvent event = new SentryEvent();
        User user = new User();
        user.setEmail("aluno@example.com");
        user.setIpAddress("203.0.113.7");
        event.setUser(user);

        Request request = new Request();
        request.setUrl("https://api.example.com/api/v1/meta");
        request.setMethod("POST");
        request.setCookies("session=abc");
        request.setQueryString("token=abc&email=aluno@example.com");
        request.setData("{\"password\":\"x\"}");
        request.setHeaders(
                new HashMap<>(
                        Map.of(
                                "Authorization",
                                "Bearer abc",
                                "cookie",
                                "session=abc",
                                "X-Fluveny-Client",
                                "panel",
                                "X-Forwarded-For",
                                "203.0.113.7",
                                "Proxy-Authorization",
                                "Basic abc",
                                "Set-Cookie",
                                "session=abc",
                                "X-Api-Key",
                                "k",
                                "User-Agent",
                                "okhttp")));
        request.getHeaders()
                .putAll(
                        Map.of(
                                "X-Real-IP",
                                "203.0.113.7",
                                "Forwarded",
                                "for=203.0.113.7",
                                "CF-Connecting-IP",
                                "203.0.113.7",
                                "True-Client-IP",
                                "203.0.113.7",
                                "X-Client-IP",
                                "203.0.113.7",
                                "X-Original-Forwarded-For",
                                "203.0.113.7"));
        request.setBodySize(42L);
        request.setEnvs(new HashMap<>(Map.of("REMOTE_ADDR", "203.0.113.7", "SERVER_NAME", "api")));
        event.setRequest(request);

        SentryEvent scrubbed = new SentryPiiScrubber().execute(event, new Hint());

        assertThat(scrubbed).isNotNull();
        assertThat(scrubbed.getUser()).isNull();
        Request result = scrubbed.getRequest();
        assertThat(result.getCookies()).isNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getQueryString()).isNull();
        assertThat(result.getBodySize()).isNull();
        assertThat(result.getHeaders()).containsOnlyKeys("User-Agent");
        assertThat(result.getEnvs()).containsOnlyKeys("SERVER_NAME");
        assertThat(result.getUrl()).isEqualTo("https://api.example.com/api/v1/meta");
    }

    @Test
    void acceptsEventsWithoutRequest() {
        SentryEvent event = new SentryEvent();

        assertThat(new SentryPiiScrubber().execute(event, new Hint())).isSameAs(event);
    }
}
