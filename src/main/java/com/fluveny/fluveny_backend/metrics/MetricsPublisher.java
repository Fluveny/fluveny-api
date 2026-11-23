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
 * <p>Os eventos do Visitante levam o {@code deviceId}; os do Aluno, o {@code metricsId}, que não é
 * o id da conta e nunca vem com e-mail, nome ou data de nascimento (história 2.1).
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

    /**
     * Uma abertura de Atividade pelo Aluno (história 2.4): o mesmo {@code ACTIVITY_STARTED} do
     * Visitante, com o {@code metricsId} no lugar do {@code deviceId}.
     */
    public void studentActivityStarted(
            UUID metricsId, String unitKey, String versionId, String activityKey) {
        append(
                new Event(
                        EventType.ACTIVITY_STARTED,
                        now(),
                        null,
                        required(unitKey, "unitKey"),
                        required(versionId, "versionId"),
                        required(activityKey, "activityKey"),
                        null,
                        metricsId(metricsId),
                        null));
    }

    /**
     * A conclusão de uma Atividade pelo Aluno (história 2.4), publicada por quem gravou o {@code
     * ActivityResult}.
     */
    public void activityCompleted(ActivityCompleted completed) {
        append(
                new Event(
                        EventType.ACTIVITY_COMPLETED,
                        now(),
                        null,
                        completed.unitKey(),
                        completed.versionId(),
                        completed.activityKey(),
                        null,
                        completed.metricsId().toString(),
                        null,
                        completed.outcome(),
                        completed.activityType(),
                        completed.resultPct(),
                        completed.firstAttempt(),
                        completed.extraHelp(),
                        completed.studyTimeMs()));
    }

    /**
     * Os campos do {@code ACTIVITY_COMPLETED} (AD-14): nada além do catálogo da spine.
     *
     * @param outcome {@code GRADED}, {@code SKIPPED} ou {@code VIEWED}
     * @param resultPct só no {@code GRADED}; nulo nos outros
     * @param firstAttempt se é a primeira conclusão da Atividade pelo Aluno
     * @param extraHelp se é uma Tentativa do Extra Help da Resposta Livre
     */
    public record ActivityCompleted(
            UUID metricsId,
            String unitKey,
            String versionId,
            String activityKey,
            String outcome,
            String activityType,
            Integer resultPct,
            boolean firstAttempt,
            boolean extraHelp,
            long studyTimeMs) {

        public ActivityCompleted {
            Objects.requireNonNull(metricsId, "metricsId");
            Objects.requireNonNull(unitKey, "unitKey");
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(activityKey, "activityKey");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(activityType, "activityType");
        }
    }

    /**
     * Uma conta de Aluno criada (história 2.1).
     *
     * @param fromVisitor se a conta veio do Visitante, com a migração do Progresso (2.6)
     */
    public void studentRegistered(UUID metricsId, boolean fromVisitor) {
        append(
                new Event(
                        EventType.STUDENT_REGISTERED,
                        now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        metricsId(metricsId),
                        fromVisitor));
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

    private static String metricsId(UUID metricsId) {
        return Objects.requireNonNull(metricsId, "metricsId").toString();
    }

    private static String required(String value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
