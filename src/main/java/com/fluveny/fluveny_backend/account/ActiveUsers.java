package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Consulta de {@code auth} a cada requisição com sessão (AD-12): a conta existe e está {@code
 * ACTIVE} ou à espera do responsável ({@code PENDING_GUARDIAN_CONSENT}, história 2.11)?
 */
@Service
public class ActiveUsers {

    private final UserStore users;

    ActiveUsers(UserStore users) {
        this.users = users;
    }

    /**
     * A conta do id, com o papel; vazio se não existe ou não está {@code ACTIVE} nem {@code
     * PENDING_GUARDIAN_CONSENT}.
     */
    public Optional<ActiveUser> find(String userId) {
        return this.users
                .findById(userId)
                .filter(user -> AccountStatus.canManage(user.status()))
                .map(user ->
                        new ActiveUser(
                                user.id(),
                                user.role(),
                                user.status() == AccountStatus.PENDING_GUARDIAN_CONSENT));
    }

    /**
     * Uma conta que entra.
     *
     * @param guardianPending se espera o responsável: lê, mas não grava Progresso
     */
    public record ActiveUser(String userId, Role role, boolean guardianPending) {}
}
