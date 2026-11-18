package com.fluveny.fluveny_backend.account;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A regra de senha, única para toda a API (história 2.1): 8 a 64 caracteres, ao menos uma letra e
 * um número {@code [ASSUMPTION]}. O teto de 64 (e de 72 bytes em UTF-8) vem do limite do BCrypt,
 * que ignoraria o resto. Use pela anotação {@link StudentPassword} na borda {@code web}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 64;

    /** O BCrypt só lê os primeiros 72 bytes. */
    static final int MAX_BYTES = 72;

    public static final String MESSAGE =
            "A senha precisa ter de 8 a 64 caracteres, com pelo menos uma letra e um número."
                    + " Senhas com muitos acentos ou emoji podem precisar ser mais curtas.";

    private PasswordPolicy() {}

    /**
     * O problema da senha, em pt-BR; vazio se ela segue a regra.
     *
     * @param password a senha (nunca vai para o log)
     */
    public static Optional<String> violation(String password) {
        if (password == null) {
            return Optional.of(MESSAGE);
        }
        int length = password.codePointCount(0, password.length());
        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);
        boolean fits = password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        return (length >= MIN_LENGTH && length <= MAX_LENGTH && letter && digit && fits)
                ? Optional.empty()
                : Optional.of(MESSAGE);
    }
}
