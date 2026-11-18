package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.platform.security.Role;

/**
 * A conta criada pelo {@link RegisterStudent}.
 *
 * @param userId o id da conta, para a sessão
 * @param role o papel ({@code STUDENT})
 * @param profile o perfil para a resposta
 */
public record RegisteredStudent(String userId, Role role, StudentProfile profile) {}
