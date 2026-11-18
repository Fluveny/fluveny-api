package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/** Visão do aluno do Grammar Time: igual à {@link GrammarActivity}, que não tem segredo. */
@JsonTypeName(GrammarActivity.TYPE)
@Schema(name = "GrammarActivityStudentView")
public record GrammarActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = GrammarActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeSentence> used,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeRule> rules,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeExample> examples)
        implements ActivityStudentView {

    public GrammarActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(used, "used");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(examples, "examples");
    }
}
