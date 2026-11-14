package com.fluveny.fluveny_backend.platform.security;

/**
 * Papéis do campo {@code role} do usuário (AD-12). A hierarquia fica em {@link
 * RoleHierarchyConfig}.
 */
public enum Role {
    STUDENT,
    CONTENT_CREATOR,
    ADMIN;

    /** A autoridade do Spring Security: {@code ROLE_<nome>}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
