package com.fluveny.fluveny_backend.account;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * O hash de senha de {@code users} (história 2.1): BCrypt do {@code spring-security-crypto}. A
 * senha e o hash nunca vão para o log nem para a resposta.
 *
 * <p>A senha é normalizada em NFC antes do BCrypt (história 2.3): o "é" composto (NFD) de um
 * teclado e o pré-composto (NFC) de outro são a mesma senha. A conferência tenta a forma NFC e, se
 * falhar, a forma recebida, para as contas criadas antes da normalização.
 */
@Component
public class Passwords {

    private final BCryptPasswordEncoder encoder;

    /**
     * Um hash fixo, de uma senha que ninguém conhece, para o e-mail desconhecido e a conta sem
     * senha: o BCrypt roda do mesmo jeito, e o tempo da resposta não revela se a conta existe.
     */
    private final String unknownHash;

    public Passwords() {
        this(new BCryptPasswordEncoder());
    }

    /** Para o teste contar as conferências. */
    Passwords(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
        this.unknownHash = encoder.encode("fluveny-sem-conta-" + System.nanoTime() + "-a1");
    }

    /** A senha em NFC: a forma que a regra confere e o BCrypt recebe. */
    public static String normalize(String password) {
        return (password == null) ? null : Normalizer.normalize(password, Normalizer.Form.NFC);
    }

    /** O hash BCrypt da senha em NFC, que precisa seguir a {@link PasswordPolicy}. */
    public String hash(String password) {
        if (PasswordPolicy.violation(password).isPresent()) {
            throw new IllegalArgumentException(PasswordPolicy.MESSAGE);
        }
        return this.encoder.encode(normalize(password));
    }

    /**
     * Confere a senha com o hash: primeiro a forma NFC, depois a recebida, se for diferente. Sem
     * hash utilizável, roda o BCrypt contra o hash fixo e devolve {@code false}.
     *
     * @param password a senha digitada (nunca vai para o log)
     * @param passwordHash o hash gravado; nulo ou vazio é conta sem senha
     */
    public boolean matches(String password, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            burn(password);
            return false;
        }
        if (password == null) {
            burn(null);
            return false;
        }
        String normalized = normalize(password);
        if (check(normalized, passwordHash)) {
            return true;
        }
        return !normalized.equals(password) && check(password, passwordHash);
    }

    /**
     * Roda o BCrypt contra o hash fixo, quantas vezes o {@link #matches} rodaria com a senha
     * errada: duas se a senha recebida não está em NFC, uma se está. O e-mail desconhecido leva o
     * mesmo tempo.
     */
    public void burn(String password) {
        if (password == null) {
            check("", this.unknownHash);
            return;
        }
        String normalized = normalize(password);
        check(normalized, this.unknownHash);
        if (!normalized.equals(password)) {
            check(password, this.unknownHash);
        }
    }

    private boolean check(String password, String passwordHash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            // O BCrypt recusa mais de 72 bytes; nenhuma senha gravada passa disso.
            this.encoder.matches("", this.unknownHash);
            return false;
        }
        try {
            return this.encoder.matches(password, passwordHash);
        } catch (IllegalArgumentException ex) {
            // Hash fora do formato BCrypt: não confere.
            return false;
        }
    }
}
