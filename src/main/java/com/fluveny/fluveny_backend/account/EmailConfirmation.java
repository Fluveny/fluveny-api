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
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Confirmação de e-mail (história 2.2, FR-2, AD-13, AD-15): emite o token de uso único em {@code
 * one_time_tokens} (só o hash) e põe o e-mail com o link no outbox de {@code mail}; consome o token
 * e grava o {@code emailVerifiedAt}; reenvia com limite por Aluno.
 *
 * <p>O token e o link nunca vão para o log, o Sentry nem a resposta: saem só no texto do e-mail.
 */
@Service
public class EmailConfirmation {

    /** Quanto vale o link {@code [ASSUMPTION]}. */
    static final Duration VALIDITY = Duration.ofDays(7);

    static final String SUBJECT = "Confirm your Fluveny email";

    static final String PATH = "/confirmar-email?token=";

    private static final Pattern PLAIN_NAME = Pattern.compile("^[\\p{L}][\\p{L} '\\-]*$");

    /** Reenvios por Aluno: 1 por minuto e 5 por dia. */
    static final int RESENDS_PER_MINUTE = 1;

    static final int RESENDS_PER_DAY = 5;

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final MailSender mail;

    private final Clock clock;

    private final String appUrl;

    private final RateLimiter perMinute;

    private final RateLimiter perDay;

    EmailConfirmation(
            UserStore users,
            OneTimeTokenStore tokens,
            MailSender mail,
            Clock clock,
            @Value("${fluveny.app.url:}") String appUrl) {
        this.users = users;
        this.tokens = tokens;
        this.mail = mail;
        this.clock = clock;
        this.appUrl = stripTrailingSlashes(appUrl.strip());
        this.perMinute = new RateLimiter(RESENDS_PER_MINUTE, Duration.ofMinutes(1), clock);
        this.perDay = new RateLimiter(RESENDS_PER_DAY, Duration.ofDays(1), clock);
    }

    /**
     * Cria o token e enfileira o e-mail (o cadastro chama logo depois de gravar a conta).
     *
     * @param name o nome para a saudação; nulo vira "Hi!"
     */
    void issue(String userId, String email, String name) {
        Instant now = now();
        String token = OpaqueTokens.generate();
        this.tokens.insert(
                new OneTimeToken(
                        OpaqueTokens.hash(token),
                        userId,
                        TokenPurpose.EMAIL_CONFIRMATION,
                        email,
                        now,
                        now.plus(VALIDITY)));
        this.mail.enqueue(email, SUBJECT, text(name, token), MailPriority.NORMAL);
    }

    /**
     * Consome o token e confirma o e-mail, se ele ainda é o da conta.
     *
     * @throws ApiException {@code 410 CONFIRMATION_LINK_INVALID} se o token é desconhecido, venceu
     *     ou já foi usado, ou se o e-mail dele não é mais o da conta (o token é gasto mesmo assim)
     */
    public void confirm(String token) {
        Optional<OneTimeToken> consumed =
                (token == null || token.isBlank())
                        ? Optional.empty()
                        : this.tokens.consume(
                                OpaqueTokens.hash(token), TokenPurpose.EMAIL_CONFIRMATION, now());
        OneTimeToken found = consumed.orElseThrow(EmailConfirmation::linkInvalid);
        // O e-mail da conta mudou (ou a conta sumiu): o token já foi gasto, e o link não vale.
        if (!this.users.markEmailVerified(found.userId(), found.email(), now())) {
            throw linkInvalid();
        }
    }

    private static ApiException linkInvalid() {
        return new ApiException(
                HttpStatus.GONE,
                "CONFIRMATION_LINK_INVALID",
                "Este link de confirmação não vale mais. Peça outro no App.");
    }

    /**
     * Apaga os links anteriores do Aluno, cria outro e enfileira o e-mail.
     *
     * @throws ApiException {@code 409 EMAIL_ALREADY_CONFIRMED} se já confirmou; {@code 429
     *     TOO_MANY_REQUESTS} (com {@code Retry-After}) acima de 1 por minuto ou 5 por dia
     */
    public void resend(String userId) {
        User user =
                this.users
                        .findById(userId)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail()));
        if (user.emailVerifiedAt() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_CONFIRMED",
                    "Seu e-mail já está confirmado.");
        }
        this.perMinute.acquire(userId);
        this.perDay.acquire(userId);
        this.tokens.deleteByUser(userId, TokenPurpose.EMAIL_CONFIRMATION);
        issue(user.id(), user.email(), user.name());
    }

    private String text(String name, String token) {
        String greeting = isPlainName(name) ? "Hi, " + name.strip() + "!" : "Hi!";
        return greeting
                + "\n\n"
                + "To confirm your Fluveny email, open the link below:\n\n"
                + this.appUrl
                + PATH
                + token
                + "\n\n"
                + "The link works for 7 days.\n\n"
                + "If you didn't create an account, you can ignore this email.\n\n"
                + "The Fluveny team\n";
    }

    /**
     * O nome entra na saudação só se for só letras, espaços, apóstrofo e hífen: o e-mail pode ir
     * para um endereço alheio, e um nome como "Clique em site.com" viraria isca.
     */
    static boolean isPlainName(String name) {
        return name != null && PLAIN_NAME.matcher(name.strip()).matches();
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    static String stripTrailingSlashes(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
