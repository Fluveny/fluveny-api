package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** O valor de uma lacuna: a palavra do banco ou o que o aluno digitou. */
@Schema(name = "GapsAnswerItem")
public record GapsAnswerItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        maxLength = 100,
                        description =
                                "O valor da lacuna, com as Variáveis do Aluno trocadas pelos"
                                        + " tokens ({name}). Em branco é 400 INVALID_ANSWER.")
                String value) {

    public GapsAnswerItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(value, "value");
    }
}
