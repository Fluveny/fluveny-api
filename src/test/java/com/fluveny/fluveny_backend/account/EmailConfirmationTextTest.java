package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * O texto do e-mail de confirmação (história 2.2), sem Spring: o link sem barra dobrada quando o
 * {@code FLUVENY_APP_URL} termina em {@code /}, e o nome na saudação só quando é só letras.
 */
class EmailConfirmationTextTest {

    private final List<String> texts = new ArrayList<>();

    private final MailSender mail = (to, subject, text, priority) -> this.texts.add(text);

    private EmailConfirmation confirmation(String appUrl) {
        return new EmailConfirmation(
                mock(UserStore.class),
                mock(OneTimeTokenStore.class),
                this.mail,
                Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneOffset.UTC),
                appUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.fluveny.test/", " https://app.fluveny.test// "})
    void theTrailingSlashOfTheAppUrlIsCut(String appUrl) {
        confirmation(appUrl).issue("0".repeat(24), "ana@example.com", "Ana");
        assertThat(this.texts.getFirst())
                .contains("\nhttps://app.fluveny.test/confirmar-email?token=")
                .doesNotContain("test//");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana", "Ana Lúcia", "D'Ávila", "Jean-Pierre", "  Zoë  "})
    void aPlainNameIsGreeted(String name) {
        confirmation("https://app.fluveny.test").issue("0".repeat(24), "a@example.com", name);
        assertThat(this.texts.getFirst()).startsWith("Olá, " + name.strip() + "!\n");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Clique em site.com",
                "https://golpe.example",
                "Ana2",
                "Ana.",
                "-Ana",
                "Ana <b>"
            })
    void anythingElseIsJustOla(String name) {
        confirmation("https://app.fluveny.test").issue("0".repeat(24), "a@example.com", name);
        assertThat(this.texts.getFirst()).startsWith("Olá!\n").doesNotContain(name.strip() + "!");
    }

    @Test
    void withoutANameItIsJustOla() {
        confirmation("https://app.fluveny.test").issue("0".repeat(24), "a@example.com", null);
        assertThat(this.texts.getFirst()).startsWith("Olá!\n");
    }
}
