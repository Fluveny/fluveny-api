/**
 * API pública da {@code platform} para os outros módulos: papéis e a declaração de rota
 * ({@code @PublicRoute}, {@code @RequiresRole}), e a sessão do usuário: a porta {@code
 * SessionAuthenticator} (que {@code auth} implementa), o {@code SessionPrincipal} da requisição, o
 * {@code SessionCookie} e o cabeçalho {@code X-Fluveny-Client} ({@code ClientHeader}).
 */
@NamedInterface("security")
package com.fluveny.fluveny_backend.platform.security;

import org.springframework.modulith.NamedInterface;
