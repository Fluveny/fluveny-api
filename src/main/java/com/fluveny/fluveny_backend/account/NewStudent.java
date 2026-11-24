package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.platform.events.StudentRegistered;
import java.time.LocalDate;

/**
 * Os dados de um cadastro de Aluno (histórias 2.1 e 2.6), já validados na borda {@code web}. A
 * senha nunca vai para o log.
 *
 * @param email o e-mail como digitado; {@link RegisterStudent} normaliza
 * @param name o nome, opcional
 * @param termsVersion a versão dos termos de uso aceita
 * @param privacyVersion a versão da política de privacidade aceita
 * @param country o país, opcional: o {@code name} em inglês de um país de {@code countries.json}
 * @param visitor os recibos do Visitante, quando o cadastro vem do convite; nulo sem eles
 * @param guardianEmail o e-mail do responsável, exigido de 12 a 17 anos (história 2.11); ignorado
 *     com 18 ou mais
 */
public record NewStudent(
        String email,
        String password,
        LocalDate birthDate,
        String name,
        String termsVersion,
        String privacyVersion,
        String country,
        StudentRegistered.Visitor visitor,
        String guardianEmail) {

    /** Um cadastro sem o responsável (histórias 2.1 e 2.6). */
    public NewStudent(
            String email,
            String password,
            LocalDate birthDate,
            String name,
            String termsVersion,
            String privacyVersion,
            String country,
            StudentRegistered.Visitor visitor) {
        this(
                email,
                password,
                birthDate,
                name,
                termsVersion,
                privacyVersion,
                country,
                visitor,
                null);
    }

    /** Um cadastro sem país e sem o Visitante (história 2.1). */
    public NewStudent(
            String email,
            String password,
            LocalDate birthDate,
            String name,
            String termsVersion,
            String privacyVersion) {
        this(email, password, birthDate, name, termsVersion, privacyVersion, null, null, null);
    }

    /** Nunca a senha. */
    @Override
    public String toString() {
        return "NewStudent[***]";
    }
}
