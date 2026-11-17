package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Visão do aluno das Lacunas: a {@link GapsActivity} sem respostas aceitas nem justificativa. O
 * banco vem na ordem do conteúdo; o App decide se embaralha.
 */
@JsonTypeName(GapsActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "GapsActivityStudentView")
public record GapsActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = GapsActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        allowableValues = {GapsActivity.MODE_BANK, GapsActivity.MODE_TYPED})
                String mode,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "As linhas, na ordem de exibição. Cada ___ (três ou mais _) é"
                                        + " uma lacuna.")
                List<ContentText> lines,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "Um item por lacuna, na ordem dos ___ nas linhas (linha a"
                                        + " linha).")
                List<GapsItemStudentView> items,
        @Schema(
                        description =
                                "O banco de palavras, só no mode bank (ausente no typed), na"
                                        + " ordem do conteúdo, com os distratores: o App"
                                        + " embaralha.")
                List<GapsWord> bank,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements ActivityStudentView {

    public GapsActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(xp, "xp");
    }
}
