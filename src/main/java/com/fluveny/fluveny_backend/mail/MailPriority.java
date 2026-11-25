package com.fluveny.fluveny_backend.mail;

import org.springframework.modulith.NamedInterface;

/**
 * A prioridade no outbox (AD-15): com a cota diária no fim, sai primeiro o {@link #HIGH}
 * (redefinição de senha), depois o {@link #NORMAL} (confirmação de e-mail).
 */
@NamedInterface("sender")
public enum MailPriority {
    HIGH,
    NORMAL
}
