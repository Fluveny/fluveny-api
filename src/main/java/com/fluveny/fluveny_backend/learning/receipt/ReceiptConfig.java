package com.fluveny.fluveny_backend.learning.receipt;

import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolve a chave dos recibos. Com {@code FLUVENY_RECEIPT_KEY}, usa a variável; sem ela (só fora de
 * {@code prod}, onde o {@code ProdEnvironmentGuard} já barrou a partida), gera uma chave aleatória
 * com um aviso no log: os recibos emitidos deixam de ser válidos quando a API reinicia.
 *
 * <p>Monta também o {@link ReceiptVerifier}, com a chave anterior de {@code
 * FLUVENY_RECEIPT_PREVIOUS_KEY} quando ela existe (rotação, história 2.6).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReceiptProperties.class)
class ReceiptConfig {

    private static final Logger log = LoggerFactory.getLogger(ReceiptConfig.class);

    @Bean
    ReceiptKey receiptKey(ReceiptProperties properties) {
        byte[] key = properties.keyBytes();
        if (key == null) {
            log.warn(
                    "Sem FLUVENY_RECEIPT_KEY: chave dos recibos do Visitante gerada na partida. Os"
                            + " recibos emitidos não valem depois que a API reiniciar.");
            key = new byte[ReceiptProperties.MIN_KEY_BYTES];
            new SecureRandom().nextBytes(key);
        }
        return new ReceiptKey(key);
    }

    @Bean
    ReceiptVerifier receiptVerifier(ReceiptKey receiptKey, ReceiptProperties properties) {
        byte[] previous = properties.previousKeyBytes();
        if (previous != null) {
            log.warn(
                    "FLUVENY_RECEIPT_PREVIOUS_KEY ativa: recibos da chave anterior ainda são aceitos."
                            + " Retire a variável 90 dias depois da troca.");
        }
        return new ReceiptVerifier(
                receiptKey, (previous == null) ? null : new ReceiptKey(previous));
    }
}
