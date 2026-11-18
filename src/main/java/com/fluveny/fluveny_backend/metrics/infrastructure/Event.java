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
        Boolean fromVisitor) {

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
