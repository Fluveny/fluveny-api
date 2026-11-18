package com.fluveny.fluveny_backend.account;

/**
 * O estado da conta (AD-13). Só {@link #ACTIVE} autentica; os outros chegam nas histórias do
 * convite (4.x), do responsável (2.11) e da desativação.
 */
public enum AccountStatus {
    INVITED,
    ACTIVE,
    PENDING_GUARDIAN_CONSENT,
    DEACTIVATED
}
