package com.fluveny.fluveny_backend.mail;

import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Trata o {@link AccountDeletionRequested} (história 2.10, AD-3), na mesma thread e na mesma
 * transação da exclusão: apaga do {@code mail_outbox} todo e-mail para o endereço da conta ou para
 * o e-mail pendente ou para o responsável da conta pendente (história 2.11), em qualquer {@code
 * status}. A confirmação da exclusão entra na fila só depois do commit e fica até sair. Uma falha
 * lança e desfaz a exclusão inteira.
 */
@Component
class OutboxDeletionHandler {

    private final OutboxStore store;

    OutboxDeletionHandler(OutboxStore store) {
        this.store = store;
    }

    // Ordem fixa: um tratador de teste (ou de outra história) roda depois e prova o rollback.
    @EventListener
    @Order(AccountDeletionRequested.HANDLER_ORDER)
    void on(AccountDeletionRequested deletion) {
        List<String> addresses = new ArrayList<>();
        addresses.add(deletion.email());
        if (deletion.pendingEmail() != null) {
            addresses.add(deletion.pendingEmail());
        }
        if (deletion.guardianEmail() != null) {
            addresses.add(deletion.guardianEmail());
        }
        this.store.deleteAllTo(addresses);
    }
}
