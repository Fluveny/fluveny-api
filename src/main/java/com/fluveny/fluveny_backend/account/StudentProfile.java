package com.fluveny.fluveny_backend.account;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O perfil mínimo do Aluno (história 2.1): o que o App mostra e usa nas Falas. Montado só pelo
 * {@link StudentProfiles}.
 *
 * @param name o nome, se o Aluno informou
 * @param email o e-mail da conta, normalizado
 * @param country o país, se o Aluno informou no cadastro (história 2.6): o {@code name} em inglês
 *     de {@code countries.json}
 * @param pronounSet os pronomes; nulo vale como {@code THEY}
 * @param emailConfirmed se o Aluno já confirmou o e-mail (história 2.2)
 * @param emailConfirmationOverdue se o e-mail segue sem confirmar 7 dias depois do cadastro: o App
 *     mostra a faixa "Confirme seu e-mail para poder recuperar a senha." (história 2.8)
 */
@Schema(name = "StudentProfile")
public record StudentProfile(
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) String email,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        nullable = true,
                        description =
                                "O país informado no cadastro: o name em inglês de um país de"
                                        + " countries.json. Nulo se o Aluno não informou.")
                String country,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        nullable = true,
                        description = "Nulo vale como THEY (they/them).")
                PronounSet pronounSet,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "Se o e-mail já foi confirmado.")
                boolean emailConfirmed,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "Se o e-mail segue sem confirmar 7 dias ou mais depois do"
                                        + " cadastro (relógio do servidor). Com true, o App"
                                        + " mostra a faixa \"Confirme seu e-mail para poder"
                                        + " recuperar a senha.\" com o Reenviar; até confirmar,"
                                        + " a recuperação de senha não envia nada.")
                boolean emailConfirmationOverdue) {

    /** Sem o e-mail, o nome nem o país. */
    @Override
    public String toString() {
        return "StudentProfile[emailConfirmed="
                + this.emailConfirmed
                + ", emailConfirmationOverdue="
                + this.emailConfirmationOverdue
                + "]";
    }
}
