package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Visão do aluno da Fala: a {@link SpeechActivity} sem as alternativas aceitas. */
@JsonTypeName(SpeechActivity.TYPE)
@Schema(name = "SpeechActivityStudentView")
public record SpeechActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = SpeechActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "A frase-alvo, exibida ao aluno.")
                ContentText target,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements ActivityStudentView {

    public SpeechActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(xp, "xp");
    }
}
