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
 * @param pendingEmail o e-mail novo da troca ainda não confirmada (história 2.9); nulo sem troca
 * @param status {@code ACTIVE} ou {@code PENDING_GUARDIAN_CONSENT} (história 2.11)
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
                boolean emailConfirmationOverdue,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        nullable = true,
                        description =
                                "O e-mail novo pedido na troca de e-mail, até o Aluno abrir o"
                                        + " link enviado a ele. Nulo sem troca pendente.")
                String pendingEmail,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        allowableValues = {"ACTIVE", "PENDING_GUARDIAN_CONSENT"},
                        description =
                                "PENDING_GUARDIAN_CONSENT: adolescente à espera do responsável."
                                        + " Lê, mas as escritas de Progresso dão 403"
                                        + " GUARDIAN_CONSENT_PENDING; o App mostra a faixa ouro."
                                        + " Ao ver ACTIVE, o App migra os recibos pelo"
                                        + " POST /students/me/visitor-migration.")
                String status) {

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
