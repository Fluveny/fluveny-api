package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.content.PublishedStructures;
import com.fluveny.fluveny_backend.content.UnitStructure;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * O resumo de um Estágio do Aluno (história 2.7, AD-9): os registros cujas {@code activityKey}s
 * estão no Estágio na versão publicada, gravados em qualquer versão. Reconcilia as conclusões da
 * Unidade como o painel ({@link StageSequence}); Atividade removida fica fora (FR-29).
 */
@Service
class StudentStageSummaries {

    static final String STAGE_NOT_COMPLETED = "STAGE_NOT_COMPLETED";

    private final PublishedStructures structures;

    private final ActivityResultStore results;

    private final StageSequence sequence;

    StudentStageSummaries(
            PublishedStructures structures, ActivityResultStore results, StageSequence sequence) {
        this.structures = structures;
        this.results = results;
        this.sequence = sequence;
    }

    StudentStageSummary summary(String userId, String unitKey, String stageKey) {
        UnitStructure unit =
                this.structures.unit(unitKey).orElseThrow(StudentStageSummaries::notFound);
        UnitStructure.Stage stage =
                unit.stages().stream()
                        .filter(candidate -> candidate.stageKey().equals(stageKey))
                        .findFirst()
                        .orElseThrow(StudentStageSummaries::notFound);
        List<ActivityResult> all = this.results.findByUser(userId);
        Map<String, UnitProgress.StageCompletion> completions =
                this.sequence.reconcile(
                        userId,
                        unit,
                        StageSequence.recordedKeys(all, unitKey),
                        this.sequence.storedByUnit(userId).getOrDefault(unitKey, Map.of()),
                        this.sequence.metricsIdOf(userId));
        if (!completions.containsKey(stageKey)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, STAGE_NOT_COMPLETED, "O Estágio ainda não foi concluído.");
        }
        Set<String> keys = new HashSet<>(stage.activityKeys());
        long xp = 0;
        int skipped = 0;
        long studyTime = 0;
        for (ActivityResult result : all) {
            if (result.unitKey().equals(unitKey) && keys.contains(result.activityKey())) {
                xp += result.xpAwarded();
                studyTime += result.studyTimeMs();
                if (result.outcome() == Outcome.SKIPPED) {
                    skipped++;
                }
            }
        }
        return new StudentStageSummary(
                unitKey,
                stageKey,
                xp,
                StudentDashboards.average(all, unitKey, keys),
                skipped,
                studyTime);
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "A Unidade ou o Estágio não existe.");
    }
}
