package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Fala completa ({@code activities/speech.schema.json}, FR-16), "It's your turn": o aluno diz a
 * frase-alvo. Tem as alternativas aceitas, que são {@code x-fluveny-secret}. Pular conclui com
 * {@code SKIPPED}.
 */
@JsonTypeName(SpeechActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "SpeechActivity")
public record SpeechActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "A frase-alvo, exibida ao aluno.")
                ContentText target,
        @ArraySchema(
                        minItems = 1,
                        arraySchema = @Schema(description = "Outras formas aceitas da frase-alvo."))
                List<ContentText> alternatives,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements Activity {

    public SpeechActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(xp, "xp");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "speech";
}
