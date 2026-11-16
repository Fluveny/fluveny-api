package com.fluveny.fluveny_backend.learning.receipt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A chave HMAC-SHA256 dos recibos, já resolvida pelo {@link ReceiptConfig}. */
public final class ReceiptKey {

    static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public ReceiptKey(byte[] key) {
        if (key == null || key.length < ReceiptProperties.MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "A chave do recibo precisa ter pelo menos "
                            + ReceiptProperties.MIN_KEY_BYTES
                            + " bytes.");
        }
        this.key = new SecretKeySpec(key, ALGORITHM);
    }

    /** HMAC-SHA256 dos bytes. */
    byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(this.key);
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public String toString() {
        return "ReceiptKey[***]";
    }
}
