package com.fluveny.fluveny_backend.account;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O perfil mínimo do Aluno (história 2.1): o que o App mostra e usa nas Falas.
 *
 * @param name o nome, se o Aluno informou
 * @param email o e-mail da conta, normalizado
 * @param pronounSet os pronomes; nulo vale como {@code THEY}
 * @param emailConfirmed se o Aluno já confirmou o e-mail (história 2.2)
 */
@Schema(name = "StudentProfile")
public record StudentProfile(
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) String email,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        nullable = true,
                        description = "Nulo vale como THEY (they/them).")
                PronounSet pronounSet,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "Se o e-mail já foi confirmado.")
                boolean emailConfirmed) {}
