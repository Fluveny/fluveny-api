package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.StudentIdentities;
import com.fluveny.fluveny_backend.content.PublishedStructures;
import com.fluveny.fluveny_backend.content.UnitStructure;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * A sequência de Estágios do Aluno (história 2.5, AD-7, AD-9, FR-9): a reconciliação das conclusões
 * em {@code unit_progress} e a trava {@code STAGE_LOCKED}, sempre sobre a versão publicada de cada
 * Unidade.
 *
 * <p>Um Estágio conclui quando os registros do Aluno ({@code activity_results}, de qualquer versão
 * e {@code outcome}) cobrem todas as {@code activityKey}s dele na versão publicada. A conclusão é
 * conferida depois de cada primeira gravação, na leitura do painel e na trava; assim um Estágio
 * coberto porque uma versão nova removeu a Atividade pendente é gravado sem nova Tentativa. Só quem
 * gravou a conclusão publica o {@code STAGE_COMPLETED}; uma falha ao publicar vai para o log e o
 * Sentry e não desfaz a gravação.
 */
@Component
class StageSequence {

    static final String STAGE_LOCKED = "STAGE_LOCKED";

    private static final Logger log = LoggerFactory.getLogger(StageSequence.class);

    private final PublishedStructures structures;

    private final ActivityResultStore results;

    private final UnitProgressStore progress;

    private final StudentIdentities identities;

    private final MetricsPublisher metrics;

    private final Clock clock;

    StageSequence(
            PublishedStructures structures,
            ActivityResultStore results,
            UnitProgressStore progress,
            StudentIdentities identities,
            MetricsPublisher metrics,
            Clock clock) {
        this.structures = structures;
        this.results = results;
        this.progress = progress;
        this.identities = identities;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Recusa a escrita num Estágio bloqueado. Liberado: o primeiro da versão publicada, o seguinte
     * a um concluído ou um já concluído (a liberação nunca volta atrás). O Estágio é o da Atividade
     * na versão publicada, mesmo que o Aluno escreva numa versão antiga; só a Atividade fora da
     * versão publicada usa o {@code stageKey} da versão pedida. Estágio fora da versão publicada
     * (ou Unidade sem versão publicada) não trava.
     *
     * @param requestedStageKey o Estágio da Atividade na versão pedida
     * @throws ApiException {@code 409 STAGE_LOCKED}
     */
    void requireUnlocked(
            String userId, String unitKey, String activityKey, String requestedStageKey) {
        Optional<UnitStructure> found = this.structures.unit(unitKey);
        if (found.isEmpty()) {
            return;
        }
        UnitStructure unit = found.get();
        String stageKey =
                unit.stages().stream()
                        .filter(stage -> stage.activityKeys().contains(activityKey))
                        .map(UnitStructure.Stage::stageKey)
                        .findFirst()
                        .orElse(requestedStageKey);
        int index = indexOf(unit, stageKey);
        if (index <= 0) {
            return;
        }
        String previous = unit.stages().get(index - 1).stageKey();
        Map<String, UnitProgress.StageCompletion> stored = stored(userId, unitKey);
        if (stored.containsKey(stageKey) || stored.containsKey(previous)) {
            return;
        }
        Map<String, UnitProgress.StageCompletion> completions =
                reconcile(
                        userId,
                        unit,
                        recordedKeys(this.results.findByUser(userId), unitKey),
                        stored,
                        metricsIdOf(userId));
        if (completions.containsKey(stageKey) || completions.containsKey(previous)) {
            return;
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                STAGE_LOCKED,
                "Conclua o Estágio anterior para desbloquear este.");
    }

    /**
     * Depois de uma primeira gravação em {@code activity_results}: grava as conclusões que ela
     * completou. Uma falha vai para o log e o Sentry e não muda a resposta; a leitura do painel
     * reconcilia de novo.
     */
    void afterFirstRecord(String userId, String unitKey, UUID metricsId) {
        try {
            Optional<UnitStructure> unit = this.structures.unit(unitKey);
            if (unit.isEmpty()) {
                return;
            }
            reconcile(
                    userId,
                    unit.get(),
                    recordedKeys(this.results.findByUser(userId), unitKey),
                    stored(userId, unitKey),
                    () -> metricsId);
        } catch (RuntimeException ex) {
            log.error("Falha ao conferir a conclusão do Estágio", ex);
            Sentry.captureException(ex);
        }
    }

    /**
     * Grava as conclusões que faltam na versão publicada da Unidade e devolve todas, as gravadas
     * antes e as novas.
     *
     * @param recordedKeys as {@code activityKey}s com registro do Aluno na Unidade
     * @param stored as conclusões já gravadas da Unidade
     * @param metricsId lido só se alguma conclusão for gravada
     */
    Map<String, UnitProgress.StageCompletion> reconcile(
            String userId,
            UnitStructure unit,
            Set<String> recordedKeys,
            Map<String, UnitProgress.StageCompletion> stored,
            Supplier<UUID> metricsId) {
        Map<String, UnitProgress.StageCompletion> completions = new LinkedHashMap<>(stored);
        for (UnitStructure.Stage stage : unit.stages()) {
            if (completions.containsKey(stage.stageKey())
                    || stage.activityKeys().isEmpty()
                    || !recordedKeys.containsAll(stage.activityKeys())) {
                continue;
            }
            Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
            if (this.progress.completeStage(
                    userId, unit.unitKey(), stage.stageKey(), unit.versionId(), now)) {
                publishStageCompleted(metricsId, unit, stage.stageKey());
            }
            completions.put(
                    stage.stageKey(), new UnitProgress.StageCompletion(unit.versionId(), now));
        }
        return completions;
    }

    /** As conclusões gravadas do Aluno, por {@code unitKey}. */
    Map<String, Map<String, UnitProgress.StageCompletion>> storedByUnit(String userId) {
        return this.progress.findByUser(userId).stream()
                .collect(Collectors.toMap(UnitProgress::unitKey, UnitProgress::stages));
    }

    /** O {@code metricsId} da conta, lido uma vez e só quando pedido. */
    Supplier<UUID> metricsIdOf(String userId) {
        return new Supplier<>() {
            private UUID metricsId;

            @Override
            public UUID get() {
                if (this.metricsId == null) {
                    this.metricsId = StageSequence.this.identities.get(userId).metricsId();
                }
                return this.metricsId;
            }
        };
    }

    static Set<String> recordedKeys(List<ActivityResult> results, String unitKey) {
        return results.stream()
                .filter(result -> result.unitKey().equals(unitKey))
                .map(ActivityResult::activityKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, UnitProgress.StageCompletion> stored(String userId, String unitKey) {
        return storedByUnit(userId).getOrDefault(unitKey, Map.of());
    }

    private static int indexOf(UnitStructure unit, String stageKey) {
        List<UnitStructure.Stage> stages = unit.stages();
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).stageKey().equals(stageKey)) {
                return i;
            }
        }
        return -1;
    }

    private void publishStageCompleted(
            Supplier<UUID> metricsId, UnitStructure unit, String stageKey) {
        try {
            this.metrics.studentStageCompleted(
                    metricsId.get(), unit.unitKey(), unit.versionId(), stageKey);
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar STAGE_COMPLETED do Aluno", ex);
            Sentry.captureException(ex);
        }
    }
}
