package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.platform.events.GuardianConsentConfirmed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Trata o {@link GuardianConsentConfirmed} (história 2.11, AD-3). Não migra nada: os recibos estão
 * no aparelho do adolescente, e o App os manda depois pelo {@code POST
 * /students/me/visitor-migration} quando vê a conta {@code ACTIVE}: é o App que dispara a migração.
 * Aqui só fica uma linha no log, sem id nem {@code metricsId}.
 */
@Component
class GuardianConsentListener {

    private static final Logger log = LoggerFactory.getLogger(GuardianConsentListener.class);

    @EventListener
    void on(GuardianConsentConfirmed event) {
        log.info("Consentimento do responsável confirmado; a migração fica para o App");
    }
}
