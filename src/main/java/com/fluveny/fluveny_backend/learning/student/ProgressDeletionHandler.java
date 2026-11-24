package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Trata o {@link AccountDeletionRequested} (história 2.10, NFR7), na mesma thread e na mesma
 * transação da exclusão: apaga o Progresso do Aluno em {@code activity_results} e {@code
 * unit_progress}. Os {@code visitor_attempts} ficam: depois da migração (2.6), nada liga o {@code
 * deviceId} à conta. Uma falha lança e desfaz a exclusão inteira.
 */
@Component
class ProgressDeletionHandler {

    private final ActivityResultStore results;

    private final UnitProgressStore progress;

    ProgressDeletionHandler(ActivityResultStore results, UnitProgressStore progress) {
        this.results = results;
        this.progress = progress;
    }

    // Ordem fixa: um tratador de teste (ou de outra história) roda depois e prova o rollback.
    @EventListener
    @Order(AccountDeletionRequested.HANDLER_ORDER)
    void on(AccountDeletionRequested deletion) {
        this.results.deleteAllFor(deletion.userId());
        this.progress.deleteAllFor(deletion.userId());
    }
}
