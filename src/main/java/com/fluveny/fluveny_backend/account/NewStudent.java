package com.fluveny.fluveny_backend.account;

import java.time.LocalDate;

/**
 * Os dados de um cadastro de Aluno (história 2.1), já validados na borda {@code web}. A senha nunca
 * vai para o log.
 *
 * @param email o e-mail como digitado; {@link RegisterStudent} normaliza
 * @param name o nome, opcional
 * @param termsVersion a versão dos termos de uso aceita
 * @param privacyVersion a versão da política de privacidade aceita
 */
public record NewStudent(
        String email,
        String password,
        LocalDate birthDate,
        String name,
        String termsVersion,
        String privacyVersion) {

    /** Nunca a senha. */
    @Override
    public String toString() {
        return "NewStudent[***]";
    }
}
