package com.fluveny.fluveny_backend.account;

/**
 * Um cadastro já conferido pelo {@link RegisterStudent#prepare}, com o hash da senha calculado uma
 * vez, fora da transação (história 2.6). Só {@code account} monta; quem chama só repassa ao {@link
 * RegisterStudent#register(PreparedStudent)}. Nunca vai para o log.
 */
public final class PreparedStudent {

    private final NewStudent student;

    private final String email;

    private final String country;

    private final String passwordHash;

    PreparedStudent(NewStudent student, String email, String country, String passwordHash) {
        this.student = student;
        this.email = email;
        this.country = country;
        this.passwordHash = passwordHash;
    }

    NewStudent student() {
        return this.student;
    }

    /** O e-mail normalizado. */
    String email() {
        return this.email;
    }

    /** O país conferido; nulo sem país. */
    String country() {
        return this.country;
    }

    String passwordHash() {
        return this.passwordHash;
    }

    /** Sem e-mail, país, senha nem hash. */
    @Override
    public String toString() {
        return "PreparedStudent[***]";
    }
}
