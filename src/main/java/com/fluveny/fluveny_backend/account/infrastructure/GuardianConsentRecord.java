package com.fluveny.fluveny_backend.account.infrastructure;

import java.time.Instant;

/**
 * Um documento de {@code guardian_consents} (dono {@code account}, AD-10, história 2.11): a prova
 * do consentimento do responsável, sem o e-mail em texto. Os hashes são pseudonimização, não
 * anonimização: o documento segue dado pessoal.
 *
 * @param userId o {@code _id} da conta do adolescente, em hex
 * @param guardianEmailHash o sha256 do e-mail normalizado do responsável, com prefixo próprio
 * @param confirmedAt quando o responsável confirmou
 * @param termsVersion a versão dos termos em vigor na confirmação
 * @param privacyVersion a versão da política em vigor na confirmação
 * @param ipHash o sha256 do IP de quem confirmou, com prefixo próprio
 */
public record GuardianConsentRecord(
        String userId,
        String guardianEmailHash,
        Instant confirmedAt,
        String termsVersion,
        String privacyVersion,
        String ipHash) {

    /** Sem id nem hashes. */
    @Override
    public String toString() {
        return "GuardianConsentRecord[confirmedAt=" + this.confirmedAt + "]";
    }
}
