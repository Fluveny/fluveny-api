package com.fluveny.fluveny_backend.metrics.infrastructure;

import com.fluveny.fluveny_backend.metrics.EventType;
import java.time.Instant;
import java.util.Objects;

/**
 * Um documento de {@code events} (AD-14). Só os campos do tipo vêm preenchidos; os outros ficam
 * nulos e não são gravados. Todo evento leva um identificador, e só um: o {@code deviceId} do
 * Visitante ou o {@code metricsId} do Aluno.
 *
 * @param deviceId o aparelho do Visitante, como texto
 * @param occurredAt a hora do servidor
 * @param metricsId o identificador de métricas do Aluno, como texto (história 2.1)
 * @param fromVisitor só no {@code STUDENT_REGISTERED}: se a conta veio do Visitante
 * @param outcome só no {@code ACTIVITY_COMPLETED}: {@code GRADED}, {@code SKIPPED} ou {@code
 *     VIEWED} (história 2.4)
 * @param activityType só no {@code ACTIVITY_COMPLETED}: o Tipo da Atividade
 * @param resultPct só no {@code ACTIVITY_COMPLETED} corrigido ({@code GRADED}): de 0 a 100
 * @param firstAttempt só no {@code ACTIVITY_COMPLETED}: se é a primeira conclusão da Atividade
 * @param extraHelp só no {@code ACTIVITY_COMPLETED}: se é uma Tentativa do Extra Help
 * @param studyTimeMs só no {@code ACTIVITY_COMPLETED}: o tempo na Atividade, medido no App
 */
public record Event(
        EventType type,
        Instant occurredAt,
        String deviceId,
        String unitKey,
        String versionId,
        String activityKey,
        String stageKey,
        String metricsId,
        Boolean fromVisitor,
        String outcome,
        String activityType,
        Integer resultPct,
        Boolean firstAttempt,
        Boolean extraHelp,
        Long studyTimeMs) {

    public Event {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if ((deviceId == null) == (metricsId == null)) {
            throw new IllegalArgumentException("O evento leva um identificador, e só um.");
        }
    }

    /** Um evento do Visitante, só com o {@code deviceId}. */
    public Event(
            EventType type,
            Instant occurredAt,
            String deviceId,
            String unitKey,
            String versionId,
            String activityKey,
            String stageKey) {
        this(type, occurredAt, deviceId, unitKey, versionId, activityKey, stageKey, null, null);
    }

    /** Um evento sem os campos do {@code ACTIVITY_COMPLETED}. */
    public Event(
            EventType type,
            Instant occurredAt,
            String deviceId,
            String unitKey,
            String versionId,
            String activityKey,
            String stageKey,
            String metricsId,
            Boolean fromVisitor) {
        this(
                type,
                occurredAt,
                deviceId,
                unitKey,
                versionId,
                activityKey,
                stageKey,
                metricsId,
                fromVisitor,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** O identificador do evento: o {@code deviceId} ou o {@code metricsId}. */
    public String identifier() {
        return (this.deviceId != null) ? this.deviceId : this.metricsId;
    }

    /**
     * A chave de dedupe do {@code STAGE_COMPLETED}, {@code identificador|unitKey|stageKey}; nula
     * nos outros tipos, que nunca colidem.
     */
    public String stageCompletionKey() {
        return (this.type == EventType.STAGE_COMPLETED)
                ? identifier() + "|" + this.unitKey + "|" + this.stageKey
                : null;
    }
}
