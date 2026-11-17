package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Palavra do banco das Lacunas. O texto pode ter {@code {name}}; o Answer leva o texto com o token,
 * nunca o valor (AD-10). Não tem segredo.
 */
@Schema(name = "GapsWord")
public record GapsWord(
        @Schema(requiredMode = RequiredMode.REQUIRED) String wordKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText text) {

    public GapsWord {
        Objects.requireNonNull(wordKey, "wordKey");
        Objects.requireNonNull(text, "text");
    }
}
