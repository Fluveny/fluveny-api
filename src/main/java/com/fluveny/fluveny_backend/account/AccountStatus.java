package com.fluveny.fluveny_backend.account;

/**
 * O estado da conta (AD-13). {@link #ACTIVE} e {@link #PENDING_GUARDIAN_CONSENT} (história 2.11)
 * entram, editam a conta e recuperam a senha ({@link #canManage}); {@link #INVITED} chega com o
 * convite (4.x) e {@link #DEACTIVATED}, com a desativação.
 */
public enum AccountStatus {
    INVITED,
    ACTIVE,
    PENDING_GUARDIAN_CONSENT,
    DEACTIVATED;

    /**
     * Se a conta entra, edita o perfil, troca ou recupera a senha, troca o e-mail e se exclui:
     * {@link #ACTIVE} e, desde a história 2.11, {@link #PENDING_GUARDIAN_CONSENT}.
     */
    public static boolean canManage(AccountStatus status) {
        return status == ACTIVE || status == PENDING_GUARDIAN_CONSENT;
    }
}
