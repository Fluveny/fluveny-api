package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.StudentProfiles;
import com.fluveny.fluveny_backend.content.PublishedStructures;
import com.fluveny.fluveny_backend.content.UnitStructure;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * A leitura do painel do Aluno (história 2.5): XP total, Badges, Estágios n de total, Resultado
 * médio, o estado de cada Estágio e o ponto de retomada, calculados a cada leitura sobre a versão
 * publicada de cada Unidade (AD-9). Reconcilia as conclusões de Estágio antes de montar ({@link
 * StageSequence}). Registro de Atividade fora da versão publicada só conta no XP (FR-29).
 */
@Service
class StudentDashboards {

    private final PublishedStructures structures;

    private final ActivityResultStore results;

    private final StageSequence sequence;

    private final StudentProfiles profiles;

    StudentDashboards(
            PublishedStructures structures,
            ActivityResultStore results,
            StageSequence sequence,
            StudentProfiles profiles) {
        this.structures = structures;
        this.results = results;
        this.sequence = sequence;
        this.profiles = profiles;
    }

    StudentDashboard dashboard(String userId) {
        String name = this.profiles.get(userId).name();
        List<ActivityResult> all = this.results.findByUser(userId);
        long xpTotal = all.stream().mapToLong(ActivityResult::xpAwarded).sum();
        Map<String, Map<String, UnitProgress.StageCompletion>> stored =
                this.sequence.storedByUnit(userId);
        Supplier<UUID> metricsId = this.sequence.metricsIdOf(userId);
        List<StudentDashboard.Unit> units = new ArrayList<>();
        for (UnitStructure unit : this.structures.units()) {
            Set<String> recorded = StageSequence.recordedKeys(all, unit.unitKey());
            Map<String, UnitProgress.StageCompletion> completions =
                    this.sequence.reconcile(
                            userId,
                            unit,
                            recorded,
                            stored.getOrDefault(unit.unitKey(), Map.of()),
                            metricsId);
            units.add(toUnit(unit, all, recorded, completions));
        }
        // badgeCount: 0 até a 3.9.
        return new StudentDashboard(name, xpTotal, 0, units);
    }

    private static StudentDashboard.Unit toUnit(
            UnitStructure unit,
            List<ActivityResult> all,
            Set<String> recorded,
            Map<String, UnitProgress.StageCompletion> completions) {
        Set<String> published = new HashSet<>();
        unit.stages().forEach(stage -> published.addAll(stage.activityKeys()));
        List<StudentDashboard.Stage> stages = new ArrayList<>();
        StudentDashboard.Next next = null;
        boolean currentFound = false;
        int completed = 0;
        for (UnitStructure.Stage stage : unit.stages()) {
            StudentDashboard.StageState state;
            if (completions.containsKey(stage.stageKey())) {
                state = StudentDashboard.StageState.COMPLETED;
                completed++;
            } else if (!currentFound) {
                state = StudentDashboard.StageState.CURRENT;
                currentFound = true;
                next = next(stage, recorded);
            } else {
                state = StudentDashboard.StageState.LOCKED;
            }
            stages.add(new StudentDashboard.Stage(stage.stageKey(), stage.title(), state));
        }
        boolean started =
                all.stream()
                        .anyMatch(result ->
                                result.unitKey().equals(unit.unitKey())
                                        && published.contains(result.activityKey()));
        return new StudentDashboard.Unit(
                unit.unitKey(),
                unit.versionId(),
                unit.order(),
                unit.title(),
                unit.comingSoon(),
                started,
                completed,
                unit.stages().size(),
                average(all, unit.unitKey(), published),
                stages,
                next);
    }

    /** A primeira Atividade sem registro do Estágio; nula se não há (Estágio vazio). */
    private static StudentDashboard.Next next(UnitStructure.Stage stage, Set<String> recorded) {
        List<String> keys = stage.activityKeys();
        for (int i = 0; i < keys.size(); i++) {
            if (!recorded.contains(keys.get(i))) {
                return new StudentDashboard.Next(stage.stageKey(), keys.get(i), i + 1, keys.size());
            }
        }
        return null;
    }

    /**
     * A média arredondada dos {@code GRADED} da Unidade cujas {@code activityKey}s estão em {@code
     * published}; nula sem nenhum. A mesma do resumo do Estágio (2.7).
     */
    static Integer average(List<ActivityResult> all, String unitKey, Set<String> published) {
        long sum = 0;
        int count = 0;
        for (ActivityResult result : all) {
            if (result.unitKey().equals(unitKey)
                    && result.outcome() == Outcome.GRADED
                    && result.resultPct() != null
                    && published.contains(result.activityKey())) {
                sum += result.resultPct();
                count++;
            }
        }
        return (count == 0) ? null : (int) Math.round((double) sum / count);
    }
}
