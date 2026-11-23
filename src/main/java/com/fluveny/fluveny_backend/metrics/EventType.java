package com.fluveny.fluveny_backend.metrics;

/**
 * Os tipos de evento gravados em {@code events} até aqui (AD-14, histórias 1.20, 2.1, 2.4 e 2.6). O
 * catálogo fechado fica nas Consistency Conventions da spine; um tipo novo ou um campo novo muda a
 * spine antes.
 */
public enum EventType {
    /** Uma abertura do player pelo EXPERIMENTAR AGORA; só o identificador. */
    VISITOR_SESSION_STARTED,

    /** Uma abertura de Atividade, inclusive ao reabrir ou refazer. */
    ACTIVITY_STARTED,

    /**
     * A primeira conclusão de uma Atividade pelo Aluno, gravada em {@code activity_results}
     * (história 2.4).
     */
    ACTIVITY_COMPLETED,

    /**
     * O Estágio coberto pelo identificador; um só por {@code (identificador, unitKey, stageKey)}.
     */
    STAGE_COMPLETED,

    /** Uma conta de Aluno criada (história 2.1); só o {@code metricsId} e o {@code fromVisitor}. */
    STUDENT_REGISTERED,

    /**
     * O Progresso do Visitante migrado para a conta no cadastro (história 2.6); só o {@code
     * metricsId}, e só se ao menos um recibo migrou.
     */
    VISITOR_MIGRATED
}
