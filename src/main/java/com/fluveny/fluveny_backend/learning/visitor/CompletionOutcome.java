package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;

/** Como uma Atividade sem correção terminou: vista ou pulada (AD-9, história 1.12). */
@Schema(name = "CompletionOutcome")
public enum CompletionOutcome {
    VIEWED(Outcome.VIEWED),
    SKIPPED(Outcome.SKIPPED);

    private final Outcome outcome;

    CompletionOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    Outcome outcome() {
        return this.outcome;
    }
}
