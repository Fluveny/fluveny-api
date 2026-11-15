package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Escreve os 401 e 403 da segurança no mesmo formato de problema do resto da API (AD-11). Rota sem
 * declaração responde {@code ACCESS_DENIED}: 401 para o anônimo, 403 para o autenticado. Todo 401
 * leva {@code WWW-Authenticate: Bearer realm="fluveny"} (RFC 9110).
 */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final Problems problems;

    ProblemSecurityHandlers(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        ErrorCode code =
                isUndeclared(request) ? ErrorCode.ACCESS_DENIED : ErrorCode.UNAUTHENTICATED;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, Problems.BEARER_CHALLENGE);
        this.problems.write(request, response, Problems.of(HttpStatus.UNAUTHORIZED, code));
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        this.problems.write(request, response, Problems.of(ErrorCode.ACCESS_DENIED));
    }

    private static boolean isUndeclared(HttpServletRequest request) {
        return request.getAttribute(RouteAuthorizationManager.UNDECLARED_ROUTE) != null;
    }
}
