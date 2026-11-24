package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Trata o {@link AccountDeletionRequested} (história 2.10, AD-13), na mesma thread e na mesma
 * transação da exclusão: apaga todas as {@code sessions} da conta e as chaves {@code account} e
 * {@code account_ip} do {@code login_throttle} do e-mail e do e-mail pendente. A chave {@code ip}
 * fica, porque não identifica a conta. Uma falha lança e desfaz a exclusão inteira.
 */
@Component
class AccountDeletionHandler {

    private final Sessions sessions;

    private final LoginThrottle throttle;

    AccountDeletionHandler(Sessions sessions, LoginThrottle throttle) {
        this.sessions = sessions;
        this.throttle = throttle;
    }

    // Ordem fixa: um tratador de teste (ou de outra história) roda depois e prova o rollback.
    @EventListener
    @Order(AccountDeletionRequested.HANDLER_ORDER)
    void on(AccountDeletionRequested deletion) {
        this.sessions.revokeAll(deletion.userId());
        this.throttle.forget(deletion.email(), deletion.pendingEmail());
    }
}
