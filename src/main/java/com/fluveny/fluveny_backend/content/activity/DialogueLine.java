package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Fala do Diálogo. {@code ask} marca a fala que pergunta o nome ou o país do aluno; a resposta fica
 * no aparelho e nunca chega à API. Não tem segredo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "DialogueLine")
public record DialogueLine(
        @Schema(requiredMode = RequiredMode.REQUIRED) String lineKey,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        description = "O nome exibido de quem fala.")
                String speaker,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText text,
        @Schema(allowableValues = {ASK_NAME, ASK_COUNTRY}) String ask) {

    public DialogueLine {
        Objects.requireNonNull(lineKey, "lineKey");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(text, "text");
    }

    /** A fala pergunta o nome do aluno. */
    public static final String ASK_NAME = "name";

    /** A fala pergunta o país do aluno. */
    public static final String ASK_COUNTRY = "country";
}
