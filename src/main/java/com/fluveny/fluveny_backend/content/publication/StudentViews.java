package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.content.activity.ActivityStudentView;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/** As respostas das rotas do aluno (história 1.7): só a visão do aluno, sem campo secreto. */
public final class StudentViews {

    private StudentViews() {}

    /**
     * Uma Unidade publicada na listagem.
     *
     * @param comingSoon Unidade publicada sem Estágios ("em breve")
     * @param versionId a versão publicada
     */
    @Schema(name = "UnitListItemStudentView")
    public record UnitListItem(
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) int order,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean comingSoon,
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId) {}

    /** O resumo de uma Unidade publicada: Estágios e Expressões Úteis. */
    @Schema(name = "UnitStudentView")
    public record Unit(
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId,
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) int order,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean comingSoon,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<StageSummary> stages,
            @Schema(requiredMode = RequiredMode.REQUIRED)
                    List<UsefulExpression> usefulExpressions) {}

    /** Um Estágio no resumo da Unidade. */
    @Schema(name = "StageSummaryStudentView")
    public record StageSummary(
            @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) int activityCount) {}

    /** Expressão Útil (FR-14). */
    @Schema(name = "UsefulExpressionStudentView")
    public record UsefulExpression(
            @Schema(requiredMode = RequiredMode.REQUIRED) String expressionKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText term,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText meaning,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<ContentText> examples) {}

    /** Um Estágio com as Atividades na visão do aluno. */
    @Schema(name = "StageStudentView")
    public record Stage(
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId,
            @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<ActivityStudentView> activities) {}
}
