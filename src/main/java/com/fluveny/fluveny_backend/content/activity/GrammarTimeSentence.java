package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Frase da aba "Você já usou" do Grammar Time: a frase real do Estágio, copiada no conteúdo, com a
 * forma em {@code **negrito**} (o destaque vem do conteúdo, não do código), e o rótulo da forma.
 * Não tem segredo.
 */
@Schema(name = "GrammarTimeSentence")
public record GrammarTimeSentence(
        @Schema(requiredMode = RequiredMode.REQUIRED) String sentenceKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText form,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText sentence) {

    public GrammarTimeSentence {
        Objects.requireNonNull(sentenceKey, "sentenceKey");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(sentence, "sentence");
    }
}
