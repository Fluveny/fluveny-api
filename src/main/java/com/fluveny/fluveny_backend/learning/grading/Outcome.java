package com.fluveny.fluveny_backend.learning.grading;

/**
 * Como uma Atividade terminou (AD-9). O corretor só produz {@link #GRADED}; {@link #SKIPPED} e
 * {@link #VIEWED} já ficam no contrato para que o App não quebre quando o Progresso os usar (um
 * valor novo de enum na resposta é mudança que quebra, AD-11).
 */
public enum Outcome {
    GRADED,
    SKIPPED,
    VIEWED
}
