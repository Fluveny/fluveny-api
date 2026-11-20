package com.fluveny.fluveny_backend.account.infrastructure;

import java.time.Instant;

/**
 * Um documento de {@code one_time_tokens} (dono {@code account}, AD-13, história 2.2): só o hash do
 * token, nunca o token.
 *
 * @param tokenHash o sha256 em hex do token, com índice único
 * @param userId o {@code _id} da conta, em hex
 * @param email o e-mail que o token confirma; um link antigo não confirma um e-mail trocado depois
 * @param expiresAt o vencimento, com TTL
 */
public record OneTimeToken(
        String tokenHash,
        String userId,
        TokenPurpose purpose,
        String email,
        Instant createdAt,
        Instant expiresAt) {

    /** Sem hash nem e-mail. */
    @Override
    public String toString() {
        return "OneTimeToken[userId=" + this.userId + ", purpose=" + this.purpose + "]";
    }
}
