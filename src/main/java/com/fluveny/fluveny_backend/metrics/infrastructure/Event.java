package com.fluveny.fluveny_backend.metrics.infrastructure;

import com.fluveny.fluveny_backend.metrics.EventType;
import java.time.Instant;
import java.util.Objects;

/**
 * Um documento de {@code events} (AD-14). Só os campos do tipo vêm preenchidos; os outros ficam
 * nulos e não são gravados.
 *
 * @param deviceId o aparelho do Visitante, como texto
 * @param occurredAt a hora do servidor
 */
public record Event(
        EventType type,
        Instant occurredAt,
        String deviceId,
        String unitKey,
        String versionId,
        String activityKey,
        String stageKey) {

    public Event {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(deviceId, "deviceId");
    }

    /**
     * A chave de dedupe do {@code STAGE_COMPLETED}, {@code deviceId|unitKey|stageKey}; nula nos
     * outros tipos, que nunca colidem.
     */
    public String stageCompletionKey() {
        return (this.type == EventType.STAGE_COMPLETED)
                ? this.deviceId + "|" + this.unitKey + "|" + this.stageKey
                : null;
    }
}
