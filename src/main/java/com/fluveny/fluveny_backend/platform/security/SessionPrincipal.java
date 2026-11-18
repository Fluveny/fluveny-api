package com.fluveny.fluveny_backend.platform.security;

import java.util.Objects;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * O usuário da sessão da requisição (AD-12, história 2.1). O {@code userId} de uma rota vem só
 * daqui, nunca do corpo, da URL ou de um cabeçalho.
 *
 * @param userId o id do usuário
 * @param role o papel do usuário
 */
public record SessionPrincipal(String userId, Role role) {

    public SessionPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }

    /**
     * O usuário da sessão da requisição atual.
     *
     * @throws AuthenticationCredentialsNotFoundException sem sessão (vira {@code 401
     *     UNAUTHENTICATED}); não acontece numa rota com {@code @RequiresRole} de papel de usuário
     */
    public static SessionPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal p) {
            return p;
        }
        throw new AuthenticationCredentialsNotFoundException("Sem sessão de usuário.");
    }

    /** Nunca o id inteiro no log. */
    @Override
    public String toString() {
        return "SessionPrincipal[role=" + this.role + "]";
    }
}
