package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/** Visão do aluno do Diálogo: igual à {@link DialogueActivity}, que não tem segredo. */
@JsonTypeName(DialogueActivity.TYPE)
@Schema(name = "DialogueActivityStudentView")
public record DialogueActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = DialogueActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<DialogueLine> lines)
        implements ActivityStudentView {

    public DialogueActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");
    }
}
