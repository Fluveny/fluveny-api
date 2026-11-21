package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Hash e conferência de senha, com a normalização NFC (história 2.3). Sem Spring. */
class PasswordsTest {

    private static final String NFC = Normalizer.normalize("café1234", Normalizer.Form.NFC);

    private static final String NFD = Normalizer.normalize("café1234", Normalizer.Form.NFD);

    private final Passwords passwords = new Passwords();

    @Test
    void theHashIsOfTheNfcForm() {
        String hash = this.passwords.hash(NFD);
        assertThat(new BCryptPasswordEncoder().matches(NFC, hash)).isTrue();
        assertThat(this.passwords.matches(NFD, hash)).isTrue();
        assertThat(this.passwords.matches(NFC, hash)).isTrue();
    }

    @Test
    void aHashOfTheReceivedFormStillMatches() {
        String legacy = new BCryptPasswordEncoder().encode(NFD);
        assertThat(this.passwords.matches(NFD, legacy)).isTrue();
    }

    @Test
    void aWrongPasswordDoesNotMatch() {
        String hash = this.passwords.hash("senha123");
        assertThat(this.passwords.matches("senha124", hash)).isFalse();
        assertThat(this.passwords.matches(null, hash)).isFalse();
    }

    @Test
    void withoutAUsableHashNothingMatches() {
        assertThat(this.passwords.matches("senha123", null)).isFalse();
        assertThat(this.passwords.matches("senha123", "")).isFalse();
        assertThat(this.passwords.matches("senha123", "nao-e-bcrypt")).isFalse();
    }

    @Test
    void aPasswordOverSeventyTwoBytesNeverMatches() {
        String hash = this.passwords.hash("senha123");
        assertThat(this.passwords.matches("á".repeat(40) + "1", hash)).isFalse();
    }

    /** 8 code points em NFC, 9 em NFD: a regra conta a forma NFC. */
    @Test
    void theRuleCountsTheNfcForm() {
        String nfd = Normalizer.normalize("abcdefé1", Normalizer.Form.NFD);
        assertThat(nfd.codePointCount(0, nfd.length())).isEqualTo(9);
        assertThat(PasswordPolicy.violation(nfd)).isEmpty();
        String shortNfd = Normalizer.normalize("abcdé12", Normalizer.Form.NFD);
        assertThat(shortNfd.codePointCount(0, shortNfd.length())).isEqualTo(8);
        assertThat(PasswordPolicy.violation(shortNfd)).isPresent();
    }

    /** Conta as conferências do BCrypt. */
    private static final class CountingEncoder extends BCryptPasswordEncoder {

        final AtomicInteger checks = new AtomicInteger();

        @Override
        protected boolean matchesNonNull(String raw, String encoded) {
            this.checks.incrementAndGet();
            return super.matchesNonNull(raw, encoded);
        }
    }

    /**
     * Com a senha errada fora de NFC, a conta existente roda o BCrypt duas vezes; o e-mail
     * desconhecido ({@code burn}) também, para o tempo não revelar a conta.
     */
    @Test
    void burnRunsAsManyChecksAsAWrongMatch() {
        CountingEncoder encoder = new CountingEncoder();
        Passwords counted = new Passwords(encoder);
        String hash = counted.hash("senha123");
        String wrongNfd = Normalizer.normalize("errada1é", Normalizer.Form.NFD);

        encoder.checks.set(0);
        assertThat(counted.matches(wrongNfd, hash)).isFalse();
        int matchChecks = encoder.checks.get();
        encoder.checks.set(0);
        counted.burn(wrongNfd);
        assertThat(matchChecks).isEqualTo(2);
        assertThat(encoder.checks.get()).isEqualTo(matchChecks);

        // Em NFC, uma de cada lado.
        encoder.checks.set(0);
        counted.matches("errada123", hash);
        assertThat(encoder.checks.get()).isEqualTo(1);
        encoder.checks.set(0);
        counted.burn("errada123");
        assertThat(encoder.checks.get()).isEqualTo(1);
    }
}
