package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.RegisteredStudent;
import com.fluveny.fluveny_backend.auth.Sessions;
import com.fluveny.fluveny_backend.platform.security.ClientHeader;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.security.SessionCookie;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de Aluno adulto (história 2.1, FR-2): cria a conta em {@code account} e já entra, com a
 * sessão criada aqui. Fica em {@code auth} porque {@code account} não chama {@code auth} (AD-3).
 */
@RestController
@Tag(name = "auth")
@PublicRoute
@RequestMapping("/api/v1/auth")
class SignupController {

    private final RegisterStudent register;

    private final Sessions sessions;

    SignupController(RegisterStudent register, Sessions sessions) {
        this.register = register;
        this.sessions = sessions;
    }

    @Operation(
            operationId = "signup",
            description =
                    "Cria a conta do Aluno adulto, já ativa, e entra. Exige X-Fluveny-Client: web"
                            + " ou android (senão 400 CLIENT_HEADER_REQUIRED). No web, a sessão"
                            + " vai só no cookie fluveny_session (HttpOnly; Secure;"
                            + " SameSite=Lax; Path=/api; 30 dias); no Android, só no"
                            + " sessionToken do corpo. Senha fora da regra, aceite vazio ou data"
                            + " de nascimento inválida dão 400 VALIDATION_FAILED; menor de 12,"
                            + " 422 AGE_BELOW_MINIMUM; de 12 a 17, 422"
                            + " GUARDIAN_CONSENT_REQUIRED; e-mail já cadastrado, 409"
                            + " EMAIL_ALREADY_REGISTERED.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SignupResponse> signup(
            @Parameter(
                            in = ParameterIn.HEADER,
                            required = true,
                            schema =
                                    @Schema(
                                            type = "string",
                                            allowableValues = {"web", "android"}))
                    @RequestHeader(name = ClientHeader.NAME, required = false)
                    String clientHeader,
            @Valid @RequestBody SignupRequest body,
            HttpServletResponse response) {
        ClientHeader client =
                ClientHeader.parse(clientHeader)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        ClientHeader.REQUIRED_CODE,
                                        "Envie o cabeçalho X-Fluveny-Client com web ou android."));
        RegisteredStudent student =
                this.register.register(
                        new NewStudent(
                                body.email(),
                                body.password(),
                                body.birthDate(),
                                body.name(),
                                body.acceptedTermsVersion(),
                                body.acceptedPrivacyVersion()));
        Sessions.NewSession session = this.sessions.create(student.userId(), student.role());
        if (client == ClientHeader.WEB) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    SessionCookie.header(session.token(), Sessions.STUDENT_TTL));
            return ApiResponse.of(new SignupResponse(student.profile(), null));
        }
        return ApiResponse.of(new SignupResponse(student.profile(), session.token()));
    }
}
