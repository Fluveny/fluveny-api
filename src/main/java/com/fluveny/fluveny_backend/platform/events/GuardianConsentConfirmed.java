package com.fluveny.fluveny_backend.platform.events;

import java.util.Objects;
import java.util.UUID;

/**
 * O responsável confirmou o consentimento do adolescente (história 2.11, FR-2, AD-3, AD-10):
 * publicado por {@code account} dentro da transação da confirmação, depois de ativar a conta. Os
 * recibos do Visitante estão no aparelho do adolescente, não no clique do responsável: {@code
 * learning} só registra a contagem, e o App migra depois pela rota própria. Sem e-mail, nome nem
 * token.
 *
 * @param userId o id da conta
 * @param metricsId o identificador de {@code events} da conta
 */
public record GuardianConsentConfirmed(String userId, UUID metricsId) {

    public GuardianConsentConfirmed {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(metricsId, "metricsId");
    }

    /** Só o tipo: nada de id nem {@code metricsId}. */
    @Override
    public String toString() {
        return "GuardianConsentConfirmed[***]";
    }
}
