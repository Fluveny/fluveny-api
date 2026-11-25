package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Consulta de {@code auth} a cada requisição com sessão (AD-12): a conta existe e está {@code
 * ACTIVE}?
 */
@Service
public class ActiveUsers {

    private final UserStore users;

    ActiveUsers(UserStore users) {
        this.users = users;
    }

    /** A conta ativa do id, com o papel; vazio se não existe ou não está {@code ACTIVE}. */
    public Optional<ActiveUser> find(String userId) {
        return this.users
                .findById(userId)
                .filter(user -> user.status() == AccountStatus.ACTIVE)
                .map(user -> new ActiveUser(user.id(), user.role()));
    }

    /** Uma conta ativa. */
    public record ActiveUser(String userId, Role role) {}
}
