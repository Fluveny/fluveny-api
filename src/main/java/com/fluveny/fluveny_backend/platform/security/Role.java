package com.fluveny.fluveny_backend.platform.security;

/**
 * Papéis do campo {@code role} do usuário (AD-12). A hierarquia fica em {@link
 * RoleHierarchyConfig}. {@link #DEPLOY} não é papel de usuário: é o do job de publicação.
 */
public enum Role {
    STUDENT,
    CONTENT_CREATOR,
    ADMIN,

    /**
     * Principal de máquina do job "Publicar conteúdo", autenticado pelo token de deploy do ambiente
     * ({@code DeployTokenFilter}, história 1.7). Fica fora da hierarquia: nenhum usuário o alcança,
     * e ele não alcança nenhum outro papel.
     */
    DEPLOY;

    /** A autoridade do Spring Security: {@code ROLE_<nome>}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
