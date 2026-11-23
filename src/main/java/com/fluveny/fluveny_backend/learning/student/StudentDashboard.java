package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * O painel do Aluno (história 2.5, FR-8 a FR-11): só números calculados no servidor, a cada
 * leitura, sobre a versão publicada de cada Unidade.
 *
 * @param name o nome da conta; ausente se o Aluno não informou
 * @param xpTotal a soma do {@code xpAwarded} de todos os registros, inclusive de Atividades
 *     removidas (o mesmo do {@code /progress})
 * @param badgeCount os Badges do Aluno; 0 até a história 3.9
 * @param units as Unidades publicadas, por {@code order}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "StudentDashboard")
public record StudentDashboard(
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description = "O nome da conta. Ausente se o Aluno não informou.")
                String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") long xpTotal,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int badgeCount,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<Unit> units) {

    public StudentDashboard {
        units = List.copyOf(units);
    }

    /** Sem o nome. */
    @Override
    public String toString() {
        return "StudentDashboard[xpTotal=" + this.xpTotal + ", units=" + this.units.size() + "]";
    }

    /**
     * Uma Unidade publicada no painel.
     *
     * @param versionId a versão publicada, base de todo o cálculo
     * @param comingSoon Unidade publicada sem Estágios ("em breve")
     * @param started se o Aluno tem registro em alguma Atividade da versão publicada da Unidade
     *     (COMEÇAR ou CONTINUAR); registro de Atividade removida não conta
     * @param stagesCompleted os Estágios da versão publicada concluídos pelo Aluno
     * @param stagesTotal os Estágios da versão publicada
     * @param averageResultPct a média arredondada do Resultado das Atividades corrigidas da versão
     *     publicada; ausente sem nenhuma
     * @param next onde retomar; ausente sem Estágio atual
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "StudentDashboardUnit")
    public record Unit(
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId,
            @Schema(requiredMode = RequiredMode.REQUIRED) int order,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean comingSoon,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean started,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int stagesCompleted,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int stagesTotal,
            @Schema(
                            requiredMode = RequiredMode.NOT_REQUIRED,
                            minimum = "0",
                            maximum = "100",
                            description =
                                    "A média arredondada do resultPct das Atividades corrigidas"
                                            + " (GRADED) da versão publicada. Puladas e"
                                            + " expositivas não entram. Ausente sem nenhuma.")
                    Integer averageResultPct,
            @Schema(requiredMode = RequiredMode.REQUIRED) List<Stage> stages,
            @Schema(
                            requiredMode = RequiredMode.NOT_REQUIRED,
                            description =
                                    "A primeira Atividade sem registro do Estágio CURRENT. Ausente"
                                            + " quando não há Estágio CURRENT.")
                    Next next) {

        public Unit {
            stages = List.copyOf(stages);
        }
    }

    /** Um Estágio da versão publicada, com o estado para o Aluno. */
    @Schema(name = "StudentDashboardStage")
    public record Stage(
            @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
            @Schema(requiredMode = RequiredMode.REQUIRED) StageState state) {}

    /**
     * O estado de um Estágio: concluído, o atual (o primeiro não concluído) ou bloqueado (os
     * seguintes não concluídos).
     */
    @Schema(name = "StudentStageState", enumAsRef = true)
    public enum StageState {
        COMPLETED,
        CURRENT,
        LOCKED
    }

    /**
     * Onde retomar: a primeira Atividade sem registro do Estágio atual.
     *
     * @param position a posição da Atividade no Estágio, a partir de 1
     * @param total as Atividades do Estágio na versão publicada
     */
    @Schema(name = "StudentDashboardNext")
    public record Next(
            @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "1") int position,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "1") int total) {}
}
