package com.fluveny.fluveny_backend.metrics;

/**
 * Os tipos de evento gravados em {@code events} até aqui (AD-14, histórias 1.20 e 2.1). O catálogo
 * fechado fica nas Consistency Conventions da spine; um tipo novo ou um campo novo muda a spine
 * antes.
 */
public enum EventType {
    /** Uma abertura do player pelo EXPERIMENTAR AGORA; só o identificador. */
    VISITOR_SESSION_STARTED,

    /** Uma abertura de Atividade, inclusive ao reabrir ou refazer. */
    ACTIVITY_STARTED,

    /**
     * O Estágio coberto pelo identificador; um só por {@code (identificador, unitKey, stageKey)}.
     */
    STAGE_COMPLETED,

    /** Uma conta de Aluno criada (história 2.1); só o {@code metricsId} e o {@code fromVisitor}. */
    STUDENT_REGISTERED
}
