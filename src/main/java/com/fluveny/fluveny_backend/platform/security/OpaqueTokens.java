package com.fluveny.fluveny_backend.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (AD-12, AD-13): 32 bytes aleatórios em base64url, guardados só como sha256 em hex.
 * Servem às sessões de {@code auth} (história 2.1) e aos tokens de uso único de {@code account}
 * (história 2.2). O token nunca vai para o log.
 */
public final class OpaqueTokens {

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {}

    /** Um token novo: 43 caracteres base64url, sem preenchimento. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O sha256 do token, em hex minúsculo: o que fica no banco. */
    public static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
