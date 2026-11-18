package com.fluveny.fluveny_backend.metrics;

import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.metrics.infrastructure.EventStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A única porta de escrita de {@code events} (AD-14): um método por evento do catálogo. Grava só o
 * identificador e os campos do tipo, nunca nome, país, texto livre, IP ou cabeçalho. O {@code
 * occurredAt} é a hora do servidor, nunca a do aparelho.
 *
 * <p>Os eventos do Visitante levam o {@code deviceId}; os do Aluno ({@code metricsId}) chegam no
 * Épico 2.
 */
@Service
public class MetricsPublisher {

    private final EventStore store;

    private final Clock clock;

    MetricsPublisher(EventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Uma abertura do player pelo Visitante. */
    public void visitorSessionStarted(UUID deviceId) {
        append(
                new Event(
                        EventType.VISITOR_SESSION_STARTED,
                        now(),
                        id(deviceId),
                        null,
                        null,
                        null,
                        null));
    }

    /** Uma abertura de Atividade pelo Visitante; o {@code unitKey} vem do conteúdo. */
    public void activityStarted(
            UUID deviceId, String unitKey, String versionId, String activityKey) {
        append(
                new Event(
                        EventType.ACTIVITY_STARTED,
                        now(),
                        id(deviceId),
                        required(unitKey, "unitKey"),
                        required(versionId, "versionId"),
                        required(activityKey, "activityKey"),
                        null));
    }

    /**
     * O Estágio coberto pelo Visitante. Grava uma vez por {@code (deviceId, unitKey, stageKey)}; a
     * repetição, inclusive concorrente, é ignorada pelo índice único.
     */
    public void stageCompleted(UUID deviceId, String unitKey, String versionId, String stageKey) {
        append(
                new Event(
                        EventType.STAGE_COMPLETED,
                        now(),
                        id(deviceId),
                        required(unitKey, "unitKey"),
                        required(versionId, "versionId"),
                        null,
                        required(stageKey, "stageKey")));
    }

    private void append(Event event) {
        this.store.append(event);
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String id(UUID deviceId) {
        return Objects.requireNonNull(deviceId, "deviceId").toString();
    }

    private static String required(String value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
