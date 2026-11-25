package com.fluveny.fluveny_backend.account;

/**
 * O estado da conta (AD-13). Só {@link #ACTIVE} autentica; os outros chegam nas histórias do
 * convite (4.x) e da desativação.
 */
public enum AccountStatus {
    INVITED,
    ACTIVE,
    DEACTIVATED
}
