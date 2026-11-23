package com.fluveny.fluveny_backend.learning.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.TestReceiptKey;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptVerifier.InvalidReceiptException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A rotação da chave dos recibos (história 2.6): com {@code FLUVENY_RECEIPT_PREVIOUS_KEY}, o recibo
 * assinado com a chave anterior ainda vale na migração; a anterior nunca assina, e uma terceira
 * chave continua recusada. O formato {@code v1} não muda.
 */
class ReceiptVerifierTest {

    private static ReceiptPayload payload() {
        return new ReceiptPayload(
                UUID.randomUUID(),
                "unit-1",
                "sha256:" + "a".repeat(64),
                "s1-greetings",
                Outcome.GRADED,
                100,
                10,
                UUID.randomUUID(),
                Instant.parse("2026-11-23T12:00:00Z"));
    }

    @Test
    void aReceiptSignedWithThePreviousKeyIsAcceptedDuringRotation() {
        String previous = TestReceiptKey.generate();
        String current = TestReceiptKey.generate();
        ReceiptConfig config = new ReceiptConfig();
        ReceiptProperties properties = new ReceiptProperties(current, previous);
        ReceiptKey currentKey = config.receiptKey(properties);
        ReceiptVerifier verifier = config.receiptVerifier(currentKey, properties);
        ReceiptPayload payload = payload();

        String oldReceipt =
                new ReceiptSigner(new ReceiptKey(Base64.getDecoder().decode(previous)))
                        .sign(payload);
        String newReceipt = new ReceiptSigner(currentKey).sign(payload);
        String foreign =
                new ReceiptSigner(
                                new ReceiptKey(
                                        Base64.getDecoder().decode(TestReceiptKey.generate())))
                        .sign(payload);

        assertThat(oldReceipt).startsWith("v1.");
        assertThat(verifier.verify(oldReceipt)).isEqualTo(payload);
        assertThat(verifier.verify(newReceipt)).isEqualTo(payload);
        assertThatThrownBy(() -> verifier.verify(foreign))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void withoutThePreviousKeyAnOldReceiptIsRefused() {
        String previous = TestReceiptKey.generate();
        ReceiptConfig config = new ReceiptConfig();
        ReceiptProperties properties = new ReceiptProperties(TestReceiptKey.generate());
        ReceiptVerifier verifier =
                config.receiptVerifier(config.receiptKey(properties), properties);
        String oldReceipt =
                new ReceiptSigner(new ReceiptKey(Base64.getDecoder().decode(previous)))
                        .sign(payload());

        assertThatThrownBy(() -> verifier.verify(oldReceipt))
                .isInstanceOf(InvalidReceiptException.class);
    }

    @Test
    void aBadPreviousKeyStopsTheStartupWithoutLeakingIt() {
        String short31 = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> new ReceiptProperties(TestReceiptKey.generate(), short31))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_RECEIPT_PREVIOUS_KEY")
                .hasMessageNotContaining(short31);
        assertThat(new ReceiptProperties(TestReceiptKey.generate(), TestReceiptKey.generate()))
                .hasToString("ReceiptProperties[key=***, previousKey=***]");
    }
}
