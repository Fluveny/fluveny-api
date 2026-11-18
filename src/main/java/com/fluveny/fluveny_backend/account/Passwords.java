package com.fluveny.fluveny_backend.account;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * O hash de senha de {@code users} (história 2.1): BCrypt do {@code spring-security-crypto}. A
 * senha e o hash nunca vão para o log nem para a resposta.
 */
@Component
public class Passwords {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** O hash BCrypt da senha, que precisa seguir a {@link PasswordPolicy}. */
    public String hash(String password) {
        if (PasswordPolicy.violation(password).isPresent()) {
            throw new IllegalArgumentException(PasswordPolicy.MESSAGE);
        }
        return this.encoder.encode(password);
    }
}
