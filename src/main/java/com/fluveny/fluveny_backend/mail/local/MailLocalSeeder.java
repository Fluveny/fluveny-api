package com.fluveny.fluveny_backend.mail.local;

import com.fluveny.fluveny_backend.mail.infrastructure.MailIndexes;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code mail} (AD-18, história 2.2). Não grava e-mails: {@code mail_outbox}
 * nasce do cadastro. Só recria os índices, que o {@code mise run reset} apaga com o banco.
 */
@Component
@Profile("local")
@Order(170)
class MailLocalSeeder implements LocalDataSeeder {

    private final MailIndexes indexes;

    MailLocalSeeder(MailIndexes indexes) {
        this.indexes = indexes;
    }

    @Override
    public String name() {
        return "mail";
    }

    @Override
    public void seed() {
        this.indexes.ensure();
    }
}
