package com.fluveny.fluveny_backend.learning.receipt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assina recibos no formato {@code v1.<base64url(JSON)>.<base64url(HMAC-SHA256)>} (história 1.12),
 * em base64url sem padding. O HMAC cobre {@code v1.<payload>}, os bytes ASCII antes do segundo
 * ponto; o {@link ReceiptVerifier} confere o mesmo trecho.
 */
@Component
public class ReceiptSigner {

    static final String VERSION = "v1";

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ReceiptKey key;

    public ReceiptSigner(ReceiptKey key) {
        this.key = key;
    }

    public String sign(ReceiptPayload payload) {
        String signed = VERSION + "." + ENCODER.encodeToString(MAPPER.writeValueAsBytes(payload));
        byte[] hmac = this.key.hmac(signed.getBytes(StandardCharsets.US_ASCII));
        return signed + "." + ENCODER.encodeToString(hmac);
    }
}
