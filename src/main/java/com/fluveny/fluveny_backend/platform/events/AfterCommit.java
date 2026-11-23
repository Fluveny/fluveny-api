package com.fluveny.fluveny_backend.platform.events;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Efeitos que só valem se a transação gravar (história 2.6): eventos de {@code metrics} e e-mail.
 * Desfeita a transação, nada roda. Sem transação ativa, roda na hora.
 */
public final class AfterCommit {

    private AfterCommit() {}

    /** Roda {@code action} depois do commit da transação atual, ou já, se não há transação. */
    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
