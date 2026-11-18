package com.fluveny.fluveny_backend.auth.infrastructure;

import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Instant;

/**
 * Um documento de {@code sessions} (dono {@code auth}, AD-12, história 2.1). Só o hash do token é
 * gravado; o token fica com o cliente.
 *
 * @param tokenHash o sha256 do token em hex, com índice único
 * @param userId o id da conta em {@code users}
 * @param role o papel da conta quando a sessão nasceu
 * @param expiresAt o vencimento, com índice TTL: o Mongo apaga a sessão vencida
 */
public record Session(
        String tokenHash, String userId, Role role, Instant createdAt, Instant expiresAt) {

    /** Sem o hash nem o usuário. */
    @Override
    public String toString() {
        return "Session[role=" + this.role + ", expiresAt=" + this.expiresAt + "]";
    }
}
