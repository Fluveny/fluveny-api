package com.fluveny.fluveny_backend.account.infrastructure;

/** Para que serve um token de {@code one_time_tokens} (AD-13). */
public enum TokenPurpose {
    /** O link de confirmação de e-mail (história 2.2). */
    EMAIL_CONFIRMATION,
    /** O link de redefinição de senha (história 2.8). */
    PASSWORD_RESET
}
