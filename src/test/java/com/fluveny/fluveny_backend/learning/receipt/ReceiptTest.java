package com.fluveny.fluveny_backend.learning.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.TestReceiptKey;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptVerifier.InvalidReceiptException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Formato, chave e verificação do recibo do Visitante (história 1.12). */
class ReceiptTest {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final ReceiptKey key = new ReceiptKey(randomBytes(32));

    private final ReceiptSigner signer = new ReceiptSigner(this.key);

    private final ReceiptVerifier verifier = new ReceiptVerifier(this.key);

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static ReceiptPayload payload() {
        return new ReceiptPayload(
                UUID.randomUUID(),
                "unit-1",
                "sha256:" + "a".repeat(64),
                "s1-greetings",
                Outcome.GRADED,
                50,
                5,
                UUID.randomUUID(),
                Instant.parse("2026-11-16T12:34:56.789Z"));
    }

    @Test
    void roundTrip() {
        ReceiptPayload payload = payload();
        String receipt = this.signer.sign(payload);
        assertThat(receipt).matches("v1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
        assertThat(this.verifier.verify(receipt)).isEqualTo(payload);
    }

    @Test
    void thePayloadIsPlainJsonWithTheSpecifiedFields() {
        String receipt = this.signer.sign(payload());
        String json =
                new String(
                        Base64.getUrlDecoder().decode(receipt.split("\\.")[1]),
                        StandardCharsets.UTF_8);
        assertThat(json)
                .contains(
                        "\"deviceId\"",
                        "\"unitKey\":\"unit-1\"",
                        "\"versionId\"",
                        "\"activityKey\":\"s1-greetings\"",
                        "\"outcome\":\"GRADED\"",
                        "\"resultPct\":50",
                        "\"xpAwarded\":5",
                        "\"nonce\"",
                        "\"issuedAt\":\"2026-11-16T12:34:56.789Z\"");
    }

    @Test
    void aTamperedPayloadIsRejected() {
        String[] parts = this.signer.sign(payload()).split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = json.replace("\"xpAwarded\":5", "\"xpAwarded\":500");
        String receipt =
                parts[0]
                        + "."
                        + B64URL.encodeToString(forged.getBytes(StandardCharsets.UTF_8))
                        + "."
                        + parts[2];
        assertThatThrownBy(() -> this.verifier.verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void aTamperedHmacIsRejected() {
        String[] parts = this.signer.sign(payload()).split("\\.");
        byte[] hmac = Base64.getUrlDecoder().decode(parts[2]);
        hmac[0] ^= 1;
        String receipt = parts[0] + "." + parts[1] + "." + B64URL.encodeToString(hmac);
        assertThatThrownBy(() -> this.verifier.verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void anotherKeyIsRejected() {
        String receipt = new ReceiptSigner(new ReceiptKey(randomBytes(32))).sign(payload());
        assertThatThrownBy(() -> this.verifier.verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "v1",
                "v1.abc",
                "v2.abc.def",
                "v1.abc.def.ghi",
                "v1.***.abc",
                "v1.abc.***"
            })
    void aMalformedReceiptIsRejected(String receipt) {
        assertThatThrownBy(() -> this.verifier.verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void aNullReceiptIsRejected() {
        assertThatThrownBy(() -> this.verifier.verify(null))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void aSignedNonPayloadIsRejected() {
        String signed = "v1." + B64URL.encodeToString("[1,2]".getBytes(StandardCharsets.UTF_8));
        String receipt =
                signed
                        + "."
                        + B64URL.encodeToString(
                                this.key.hmac(signed.getBytes(StandardCharsets.US_ASCII)));
        assertThatThrownBy(() -> this.verifier.verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);

        String empty = "v1." + B64URL.encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        String emptyReceipt =
                empty
                        + "."
                        + B64URL.encodeToString(
                                this.key.hmac(empty.getBytes(StandardCharsets.US_ASCII)));
        assertThatThrownBy(() -> this.verifier.verify(emptyReceipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void aKeyWrappedInLinesIsAccepted() {
        byte[] bytes = randomBytes(48);
        String wrapped =
                Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                .encodeToString(bytes)
                        + "\n";
        assertThat(wrapped).contains("\n");
        assertThat(new ReceiptProperties(wrapped).keyBytes()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void withoutAKeyTheConfigGeneratesOne(String key) {
        ReceiptConfig config = new ReceiptConfig();
        ReceiptKey generated = config.receiptKey(new ReceiptProperties(key));
        ReceiptKey nullKey = config.receiptKey(new ReceiptProperties(null));
        ReceiptPayload payload = payload();
        String receipt = new ReceiptSigner(generated).sign(payload);
        assertThat(new ReceiptVerifier(generated).verify(receipt)).isEqualTo(payload);
        // Cada partida gera outra chave.
        assertThatThrownBy(() -> new ReceiptVerifier(nullKey).verify(receipt))
                .isInstanceOf(InvalidReceiptException.class);
        String other = new ReceiptSigner(nullKey).sign(payload);
        assertThat(new ReceiptVerifier(nullKey).verify(other)).isEqualTo(payload);
    }

    @Test
    void theKeyMustBeBase64WithAtLeast32Bytes() {
        assertThat(new ReceiptProperties(TestReceiptKey.generate()).keyBytes()).hasSize(32);
        assertThat(new ReceiptProperties("").keyBytes()).isNull();
        assertThat(new ReceiptProperties(null).keyBytes()).isNull();
        String short31 = Base64.getEncoder().encodeToString(randomBytes(31));
        assertThatThrownBy(() -> new ReceiptProperties(short31))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_RECEIPT_KEY")
                .hasMessageNotContaining(short31);
        assertThatThrownBy(() -> new ReceiptProperties("not base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_RECEIPT_KEY");
        assertThat(new ReceiptProperties(TestReceiptKey.generate()).toString())
                .isEqualTo("ReceiptProperties[key=***]");
    }
}
