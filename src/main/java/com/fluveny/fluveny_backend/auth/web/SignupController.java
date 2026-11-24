package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.auth.Signup;
import com.fluveny.fluveny_backend.platform.events.StudentRegistered;
import com.fluveny.fluveny_backend.platform.security.ClientHeader;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de Aluno adulto (histórias 2.1 e 2.6, FR-2, FR-7): cria a conta em {@code account},
 * migra o Progresso do Visitante quando vem com os recibos e já entra, numa única transação ({@link
 * Signup}). Fica em {@code auth} porque {@code account} não chama {@code auth} (AD-3).
 */
@RestController
@Tag(name = "auth")
@PublicRoute
@RequestMapping("/api/v1/auth")
class SignupController {

    private final Signup signup;

    SignupController(Signup signup) {
        this.signup = signup;
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
                            + " 422 AGE_BELOW_MINIMUM; de 12 a 17 sem guardianEmail, 422"
                            + " GUARDIAN_CONSENT_REQUIRED (com guardianEmail, a conta nasce"
                            + " PENDING_GUARDIAN_CONSENT, o responsável recebe o link e o bloco"
                            + " visitor é ignorado); guardianEmail igual ao e-mail, 400"
                            + " VALIDATION_FAILED; e-mail já cadastrado, 409"
                            + " EMAIL_ALREADY_REGISTERED. country fora de countries.json dá 400"
                            + " VALIDATION_FAILED no campo country. Vindo do convite do Visitante,"
                            + " visitor leva o deviceId e os recibos das rotas do Visitante: os"
                            + " válidos viram o Progresso e o XP da conta, na mesma transação do"
                            + " cadastro, e os inválidos (adulterados, de outro aparelho, com mais"
                            + " de 90 dias, reemitidos, já usados ou repetidos) são descartados"
                            + " sem erro. A resposta é a mesma com ou sem recibos descartados; o"
                            + " App limpa a loja do Visitante depois do 201.")
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
        ClientHeader client = SessionResponses.requireClient(clientHeader);
        StudentRegistered.Visitor visitor =
                (body.visitor() == null)
                        ? null
                        : new StudentRegistered.Visitor(
                                body.visitor().deviceId(), body.visitor().receipts());
        Signup.Registered registered =
                this.signup.signup(
                        new NewStudent(
                                body.email(),
                                body.password(),
                                body.birthDate(),
                                body.name(),
                                body.acceptedTermsVersion(),
                                body.acceptedPrivacyVersion(),
                                body.country(),
                                visitor,
                                body.guardianEmail()));
        return SessionResponses.deliver(
                client, registered.student().profile(), registered.session(), response);
    }
}
