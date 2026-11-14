package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.platform.observability.SentryPiiScrubber;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Com um {@code SENTRY_DSN}, o Sentry liga e o {@code beforeSend} ativo é o {@link
 * SentryPiiScrubber} (NFR17). O DSN aponta para uma porta local fechada: nada sai da máquina. O
 * Sentry é global na JVM, então o teste o fecha no fim, para os outros continuarem com ele
 * desligado.
 */
@SpringBootTest
@DirtiesContext
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "SENTRY_DSN=http://publickey@127.0.0.1:9/1"
        })
class SentryWiringTest {

    @Autowired private SentryPiiScrubber scrubber;

    @AfterAll
    static void closeSentry() {
        Sentry.close();
    }

    @Test
    void activeBeforeSendIsThePiiScrubber() {
        assertThat(Sentry.isEnabled()).isTrue();
        assertThat(Sentry.getCurrentScopes().getOptions().getBeforeSend()).isSameAs(this.scrubber);
        assertThat(Sentry.getCurrentScopes().getOptions().isSendDefaultPii()).isFalse();
    }
}
