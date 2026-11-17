package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Visão do aluno do Associar: a {@link MatchActivity} sem gabarito nem justificativa. As respostas
 * vêm na ordem do conteúdo; o App decide se embaralha.
 */
@JsonTypeName(MatchActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MatchActivityStudentView")
public record MatchActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = MatchActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MatchItemStudentView> items,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MatchResponse> responses,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements ActivityStudentView {

    public MatchActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(responses, "responses");
        Objects.requireNonNull(xp, "xp");
    }
}
