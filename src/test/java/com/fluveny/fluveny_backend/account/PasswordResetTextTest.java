package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.MailSender;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * O texto do e-mail de redefinição (história 2.8), sem Spring: assunto, prioridade, link sem barra
 * dobrada e a saudação pela regra do {@code isPlainName}.
 */
class PasswordResetTextTest {

    private static final Instant NOW = Instant.parse("2026-11-23T15:00:00Z");

    private final List<String> texts = new ArrayList<>();

    private final List<String> subjects = new ArrayList<>();

    private final List<MailPriority> priorities = new ArrayList<>();

    private final MailSender mail =
            new MailSender() {
                @Override
                public void enqueue(String to, String subject, String text, MailPriority priority) {
                    PasswordResetTextTest.this.subjects.add(subject);
                    PasswordResetTextTest.this.texts.add(text);
                    PasswordResetTextTest.this.priorities.add(priority);
                }

                @Override
                public void enqueuePurgedOnSend(
                        String to, String subject, String text, MailPriority priority) {
                    enqueue(to, subject, text, priority);
                }
            };

    private void request(String appUrl, String name) {
        UserStore users = mock(UserStore.class);
        when(users.findByEmail("ana@example.com"))
                .thenReturn(
                        Optional.of(
                                new User(
                                        "0".repeat(24),
                                        "ana@example.com",
                                        "hash",
                                        LocalDate.of(1995, 5, 20),
                                        Role.STUDENT,
                                        AccountStatus.ACTIVE,
                                        UUID.randomUUID(),
                                        name,
                                        null,
                                        "0.1",
                                        "0.2",
                                        NOW,
                                        NOW,
                                        NOW,
                                        null,
                                        null)));
        new PasswordReset(
                        users,
                        mock(OneTimeTokenStore.class),
                        new Passwords(),
                        this.mail,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        appUrl)
                .request("ana@example.com");
    }

    @Test
    void theMailIsPlainTextHighPriorityWithTheLink() {
        request("https://app.fluveny.test", "Ana");
        assertThat(this.subjects).containsExactly("Reset your Fluveny password");
        assertThat(this.priorities).containsExactly(MailPriority.HIGH);
        assertThat(this.texts.getFirst())
                .startsWith("Hi, Ana!\n")
                .containsPattern(
                        "\nhttps://app\\.fluveny\\.test/redefinir-senha\\?token=[A-Za-z0-9_-]{43}\n")
                .contains("The link works for 1 hour and only once.")
                .contains(
                        "If you didn't ask for this, you can ignore this email. Your password stays the same.")
                .doesNotContain("<");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.fluveny.test/", " https://app.fluveny.test// "})
    void theTrailingSlashOfTheAppUrlIsCut(String appUrl) {
        request(appUrl, "Ana");
        assertThat(this.texts.getFirst())
                .contains("\nhttps://app.fluveny.test/redefinir-senha?token=")
                .doesNotContain("test//");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Clique em site.com", "https://golpe.example", "Ana2", "Ana <b>"})
    void aNameThatIsNotPlainIsJustOla(String name) {
        request("https://app.fluveny.test", name);
        assertThat(this.texts.getFirst()).startsWith("Hi!\n").doesNotContain(name.strip() + "!");
    }

    @Test
    void withoutANameItIsJustOla() {
        request("https://app.fluveny.test", null);
        assertThat(this.texts.getFirst()).startsWith("Hi!\n");
    }
}
