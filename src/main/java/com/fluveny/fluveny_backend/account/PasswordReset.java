package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.MailSender;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Redefinição de senha, o lado da conta (história 2.8, FR-2, AD-12, AD-13, AD-15): emite o token
 * {@code PASSWORD_RESET} de uso único em {@code one_time_tokens} (só o hash, 1 h) e põe o e-mail
 * com o link no outbox com prioridade {@code HIGH}; consome o token e grava o hash da senha nova
 * com condição. {@code auth} chama os dois (AD-3): o pedido sai da requisição pelo executor da
 * recuperação, e a redefinição encerra as sessões depois.
 *
 * <p>Só conta {@code ACTIVE} com o e-mail confirmado recebe o link ({@code [ASSUMPTION]} da FR-2).
 * O token, o link, a senha e o e-mail nunca vão para o log, o Sentry nem a resposta: saem só no
 * texto do e-mail.
 */
@Service
public class PasswordReset {

    /** Quanto vale o link. */
    static final Duration VALIDITY = Duration.ofHours(1);

    static final String SUBJECT = "Reset your Fluveny password";

    static final String PATH = "/redefinir-senha?token=";

    static final String LINK_INVALID = "PASSWORD_RESET_LINK_INVALID";

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final Passwords passwords;

    private final MailSender mail;

    private final Clock clock;

    private final String appUrl;

    PasswordReset(
            UserStore users,
            OneTimeTokenStore tokens,
            Passwords passwords,
            MailSender mail,
            Clock clock,
            @Value("${fluveny.app.url:}") String appUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.mail = mail;
        this.clock = clock;
        this.appUrl = EmailConfirmation.stripTrailingSlashes(appUrl.strip());
    }

    /**
     * Emite o link para o e-mail, se ele é de uma conta {@code ACTIVE} e confirmada: cria outro e
     * enfileira o e-mail. Os links anteriores seguem válidos até vencer, para um terceiro que saiba
     * o e-mail não invalidar o que o titular já recebeu; o uso de um apaga os outros. Qualquer
     * outro caso (desconhecido, sem confirmar, convidado, pendente, desativado) não grava nem envia
     * nada.
     *
     * <p>Roda fora da requisição (o executor da recuperação em {@code auth}): o tempo da resposta
     * não depende de a conta existir.
     *
     * @param email como digitado; normalizado como no cadastro
     */
    public void request(String email) {
        if (email == null) {
            return;
        }
        Optional<User> found = this.users.findByEmail(RegisterStudent.normalizeEmail(email));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (user.status() != AccountStatus.ACTIVE || user.emailVerifiedAt() == null) {
            return;
        }
        Instant now = now();
        String token = OpaqueTokens.generate();
        this.tokens.insert(
                new OneTimeToken(
                        OpaqueTokens.hash(token),
                        user.id(),
                        TokenPurpose.PASSWORD_RESET,
                        user.email(),
                        now,
                        now.plus(VALIDITY)));
        this.mail.enqueue(user.email(), SUBJECT, text(user.name(), token), MailPriority.HIGH);
    }

    /**
     * Consome o token e grava a senha nova, se a conta ainda está {@code ACTIVE}, confirmada e com
     * o e-mail do token. O uso apaga os outros links de redefinição da conta. Não mexe nas sessões
     * nem no {@code login_throttle}: quem chama ({@code auth}) encerra as sessões.
     *
     * @param password a senha nova, na regra única (conferida antes de gastar o token)
     * @return o id da conta, para encerrar as sessões
     * @throws ApiException {@code 400 VALIDATION_FAILED} se a senha está fora da regra (o token
     *     fica intacto); {@code 410 PASSWORD_RESET_LINK_INVALID} se o token é desconhecido, venceu
     *     ou já foi usado, ou se a conta mudou depois do pedido (o token é gasto mesmo assim)
     */
    public String reset(String token, String password) {
        if (PasswordPolicy.violation(password).isPresent()) {
            throw RegisterStudent.invalid("password", PasswordPolicy.MESSAGE);
        }
        Optional<OneTimeToken> consumed =
                (token == null || token.isBlank())
                        ? Optional.empty()
                        : this.tokens.consume(
                                OpaqueTokens.hash(token), TokenPurpose.PASSWORD_RESET, now());
        OneTimeToken found = consumed.orElseThrow(PasswordReset::linkInvalid);
        this.tokens.deleteByUser(found.userId(), TokenPurpose.PASSWORD_RESET);
        String hash = this.passwords.hash(password);
        // Desativada, sem confirmar ou com outro e-mail desde o pedido: o link não vale.
        if (!this.users.updatePasswordHash(found.userId(), found.email(), hash)) {
            throw linkInvalid();
        }
        return found.userId();
    }

    private static ApiException linkInvalid() {
        return new ApiException(
                HttpStatus.GONE,
                LINK_INVALID,
                "Este link de redefinição não vale mais. Peça outro.");
    }

    private String text(String name, String token) {
        String greeting = EmailConfirmation.isPlainName(name) ? "Hi, " + name.strip() + "!" : "Hi!";
        return greeting
                + "\n\n"
                + "To reset your Fluveny password, open the link below:\n\n"
                + this.appUrl
                + PATH
                + token
                + "\n\n"
                + "The link works for 1 hour and only once.\n\n"
                + "If you didn't ask for this, you can ignore this email. Your password stays the same.\n\n"
                + "The Fluveny team\n";
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
