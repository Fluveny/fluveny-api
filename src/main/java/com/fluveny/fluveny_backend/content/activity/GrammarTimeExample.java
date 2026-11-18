package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Exemplo da aba "Na prática" do Grammar Time. Não tem segredo. */
@Schema(name = "GrammarTimeExample")
public record GrammarTimeExample(
        @Schema(requiredMode = RequiredMode.REQUIRED) String exampleKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText text) {

    public GrammarTimeExample {
        Objects.requireNonNull(exampleKey, "exampleKey");
        Objects.requireNonNull(text, "text");
    }
}
