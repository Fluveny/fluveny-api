package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.account.StudentProfile;
import com.fluveny.fluveny_backend.auth.Sessions;
import com.fluveny.fluveny_backend.platform.security.ClientHeader;
import com.fluveny.fluveny_backend.platform.security.SessionCookie;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * A entrega da sessão, a mesma no cadastro (história 2.1) e no login (história 2.3): no web, o
 * token vai só no cookie; no Android, só no {@code sessionToken} do corpo.
 */
final class SessionResponses {

    private SessionResponses() {}

    /** O cliente do cabeçalho; {@code 400 CLIENT_HEADER_REQUIRED} se falta ou é desconhecido. */
    static ClientHeader requireClient(String header) {
        return ClientHeader.parse(header)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.BAD_REQUEST,
                                ClientHeader.REQUIRED_CODE,
                                "Envie o cabeçalho X-Fluveny-Client com web ou android."));
    }

    /** O corpo da resposta e, no web, o {@code Set-Cookie}. */
    static ApiResponse<SignupResponse> deliver(
            ClientHeader client,
            StudentProfile profile,
            Sessions.NewSession session,
            HttpServletResponse response) {
        if (client == ClientHeader.WEB) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    SessionCookie.header(session.token(), Sessions.STUDENT_TTL));
            return ApiResponse.of(new SignupResponse(profile, null));
        }
        return ApiResponse.of(new SignupResponse(profile, session.token()));
    }
}
