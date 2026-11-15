package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/** Visão do aluno da Escolha: a {@link ChoiceActivity} sem gabarito nem justificativa. */
@JsonTypeName(ChoiceActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ChoiceActivityStudentView")
public record ChoiceActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = ChoiceActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ChoiceItemStudentView> items,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements ActivityStudentView {

    public ChoiceActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(xp, "xp");
    }
}
