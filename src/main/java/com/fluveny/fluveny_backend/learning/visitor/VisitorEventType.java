package com.fluveny.fluveny_backend.learning.visitor;

/** Os eventos que o App do Visitante envia (AD-14, história 1.20). */
public enum VisitorEventType {
    /** Cada abertura do player pelo EXPERIMENTAR AGORA. */
    VISITOR_SESSION_STARTED,

    /** Cada abertura de Atividade, inclusive ao reabrir ou refazer. */
    ACTIVITY_STARTED
}
