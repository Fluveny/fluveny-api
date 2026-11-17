package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Formato de {@code FLUVENY_WEB_ORIGINS} (história 1.22): {@code scheme://host[:porta]}. */
class WebOriginsPropertiesTest {

    @Test
    void acceptsExplicitOrigins() {
        assertThat(
                        new WebOriginsProperties(
                                        List.of(
                                                "https://app.fluveny.com",
                                                " http://localhost:8081 ",
                                                "http://192.168.0.10:8081",
                                                ""))
                                .origins())
                .containsExactly(
                        "https://app.fluveny.com",
                        "http://localhost:8081",
                        "http://192.168.0.10:8081");
    }

    @Test
    void emptyMeansNoOrigin() {
        assertThat(new WebOriginsProperties(null).origins()).isEmpty();
        assertThat(new WebOriginsProperties(List.of()).origins()).isEmpty();
    }

    @Test
    void acceptsANonDefaultPort() {
        assertThat(
                        new WebOriginsProperties(
                                        List.of("https://h.test:8443", "http://h.test:443"))
                                .origins())
                .containsExactly("https://h.test:8443", "http://h.test:443");
    }

    @Test
    void rejectsOnlySeparators() {
        // "," e " , " passam pelo ProdEnvironmentGuard e chegam aqui como entradas vazias.
        assertThatThrownBy(() -> new WebOriginsProperties(List.of("", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_WEB_ORIGINS");
        assertThatThrownBy(() -> new WebOriginsProperties(List.of(" ", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_WEB_ORIGINS");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "*",
                "https://*.fluveny.com",
                "https://app.fluveny.com/",
                "https://app.fluveny.com/app",
                "https://app.fluveny.com?x=1",
                "https://app.fluveny.com#x",
                "https://user@app.fluveny.com",
                "app.fluveny.com",
                "localhost:8081",
                "ftp://app.fluveny.com",
                "null",
                "https://",
                "https://app.fluveny.com:",
                "http://localhost:0",
                "http://localhost:99999",
                "https://app.fluveny.com:443",
                "http://localhost:80"
            })
    void rejectsAnythingButAnOrigin(String origin) {
        assertThatThrownBy(() -> new WebOriginsProperties(List.of(origin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_WEB_ORIGINS");
    }
}
