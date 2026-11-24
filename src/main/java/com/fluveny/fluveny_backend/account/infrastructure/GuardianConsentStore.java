package com.fluveny.fluveny_backend.account.infrastructure;

import java.util.List;

/** Coleção {@code guardian_consents} (dona: {@code account}, AD-10). Só {@code account} chama. */
public interface GuardianConsentStore {

    void insert(GuardianConsentRecord consent);

    /** Os consentimentos da conta. */
    List<GuardianConsentRecord> findByUser(String userId);

    /** Apaga os consentimentos da conta (a exclusão, história 2.10). */
    void deleteAllFor(String userId);
}
