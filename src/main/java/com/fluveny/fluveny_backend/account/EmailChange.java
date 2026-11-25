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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Troca de e-mail (história 2.9, FR-5, AD-13): com a senha atual ({@link AccountPassword}), grava o
 * {@code pendingEmail}, emite o token {@code EMAIL_CHANGE} de uso único (só o hash, 24 h) e põe no
 * outbox, com prioridade {@code HIGH}, o link para o endereço novo e um aviso sem link para o
 * antigo. O link troca o {@code email} pelo {@code pendingEmail} numa só atualização condicional; o
 * índice único do e-mail decide a corrida com um cadastro ou outra troca.
 *
 * <p>O token, o link e os e-mails nunca vão para o log, o Sentry nem a resposta.
 */
@Service
public class EmailChange {

    static final Duration VALIDITY = Duration.ofHours(24);

    static final String SUBJECT = "Confirm your new Fluveny email";

    static final String NOTICE_SUBJECT = "Someone asked to change your Fluveny email";

    static final String PATH = "/confirmar-novo-email?token=";

    static final String LINK_INVALID = "EMAIL_CHANGE_LINK_INVALID";

    static final String ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";

    private static final Logger log = LoggerFactory.getLogger(EmailChange.class);

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final AccountPassword password;

    private final MailSender mail;

    private final Clock clock;

    private final String appUrl;

    private final RateLimiter perMinute;

    private final RateLimiter perDay;

    /** Pedidos por conta: 1 por minuto e 5 por dia. */
    static final int REQUESTS_PER_MINUTE = 1;

    static final int REQUESTS_PER_DAY = 5;

    static final String CHANGED_SUBJECT = "Your Fluveny email was changed";

    EmailChange(
            UserStore users,
            OneTimeTokenStore tokens,
            AccountPassword password,
            MailSender mail,
            Clock clock,
            @Value("${fluveny.app.url:}") String appUrl) {
        this.users = users;
        this.tokens = tokens;
        this.password = password;
        this.mail = mail;
        this.clock = clock;
        this.appUrl = EmailConfirmation.stripTrailingSlashes(appUrl.strip());
        this.perMinute = new RateLimiter(REQUESTS_PER_MINUTE, Duration.ofMinutes(1), clock);
        this.perDay = new RateLimiter(REQUESTS_PER_DAY, Duration.ofDays(1), clock);
    }

    /**
     * Pede a troca: um pedido novo substitui o anterior (o link anterior deixa de valer).
     *
     * @param newEmail como digitado; normalizado como no cadastro
     * @throws ApiException {@code 400 CURRENT_PASSWORD_INVALID}, {@code 429 TOO_MANY_REQUESTS},
     *     {@code 400 VALIDATION_FAILED} se o e-mail novo é o atual, {@code 429} acima de 1 pedido
     *     por minuto ou 5 por dia da conta, ou {@code 409 EMAIL_ALREADY_REGISTERED} se é de outra
     *     conta; em todos, nada é gravado
     */
    public void request(String userId, String newEmail, String currentPassword) {
        User user = this.password.verifyCurrent(userId, currentPassword);
        String email = RegisterStudent.normalizeEmail(newEmail);
        if (email.equals(user.email())) {
            throw RegisterStudent.invalid("newEmail", "Este já é o e-mail da sua conta.");
        }
        if (this.users.findByEmail(email).isPresent()) {
            throw alreadyRegistered();
        }
        this.perMinute.acquire(user.id());
        this.perDay.acquire(user.id());
        if (!this.users.setPendingEmail(user.id(), email)) {
            throw new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail());
        }
        this.tokens.deleteByUser(user.id(), TokenPurpose.EMAIL_CHANGE);
        try {
            Instant now = now();
            String token = OpaqueTokens.generate();
            this.tokens.insert(
                    new OneTimeToken(
                            OpaqueTokens.hash(token),
                            user.id(),
                            TokenPurpose.EMAIL_CHANGE,
                            email,
                            now,
                            now.plus(VALIDITY)));
            this.mail.enqueue(
                    email, SUBJECT, confirmationText(user.name(), token), MailPriority.HIGH);
            this.mail.enqueue(
                    user.email(),
                    NOTICE_SUBJECT,
                    noticeText(user.name(), email),
                    MailPriority.HIGH);
        } catch (RuntimeException ex) {
            // Sem link que funcione, o pedido não fica pendente.
            this.users.clearPendingEmail(user.id(), email);
            this.tokens.deleteByUser(user.id(), TokenPurpose.EMAIL_CHANGE);
            throw ex;
        }
    }

    /**
     * Consome o token e troca o e-mail da conta pelo novo, já confirmado. Apaga os links de
     * confirmação e de redefinição da conta; as sessões seguem.
     *
     * @throws ApiException {@code 410 EMAIL_CHANGE_LINK_INVALID} se o token é desconhecido, venceu,
     *     já foi usado ou é de um pedido substituído; {@code 409 EMAIL_ALREADY_REGISTERED} se outra
     *     conta pegou o e-mail antes (o pedido é desfeito)
     */
    public void confirm(String token) {
        Optional<OneTimeToken> consumed =
                (token == null || token.isBlank())
                        ? Optional.empty()
                        : this.tokens.consume(
                                OpaqueTokens.hash(token), TokenPurpose.EMAIL_CHANGE, now());
        OneTimeToken found = consumed.orElseThrow(EmailChange::linkInvalid);
        Optional<User> before = this.users.findById(found.userId());
        switch (this.users.promotePendingEmail(found.userId(), found.email(), now())) {
            case PROMOTED -> {
                this.tokens.deleteByUser(found.userId(), TokenPurpose.EMAIL_CHANGE);
                this.tokens.deleteByUser(found.userId(), TokenPurpose.EMAIL_CONFIRMATION);
                this.tokens.deleteByUser(found.userId(), TokenPurpose.PASSWORD_RESET);
                before.ifPresent(old -> notifyChanged(old, found.email()));
            }
            case NOT_PENDING -> throw linkInvalid();
            case TAKEN -> {
                this.users.clearPendingEmail(found.userId(), found.email());
                throw alreadyRegistered();
            }
        }
    }

    /** {@code n***@d***}: a primeira letra do usuário e a do domínio. */
    static String mask(String email) {
        int at = email.indexOf('@');
        String local = (at > 0) ? email.substring(0, 1) : "";
        String domain = (at >= 0 && at + 1 < email.length()) ? email.substring(at + 1, at + 2) : "";
        return local + "***@" + domain + "***";
    }

    private static ApiException linkInvalid() {
        return new ApiException(
                HttpStatus.GONE, LINK_INVALID, "Este link não vale mais. Peça a troca de novo.");
    }

    private static ApiException alreadyRegistered() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ALREADY_REGISTERED,
                "Este e-mail já tem uma conta. Entre ou recupere a senha.");
    }

    private static String greeting(String name) {
        return EmailConfirmation.isPlainName(name) ? "Hi, " + name.strip() + "!" : "Hi!";
    }

    private String confirmationText(String name, String token) {
        return greeting(name)
                + "\n\n"
                + "To use this email for your Fluveny account, open the link below:\n\n"
                + this.appUrl
                + PATH
                + token
                + "\n\n"
                + "The link works for 24 hours and only once.\n\n"
                + "If you didn't ask for this, you can ignore this email.\n\n"
                + "The Fluveny team\n";
    }

    private static String noticeText(String name, String newEmail) {
        return greeting(name)
                + "\n\n"
                + "Someone asked to change the email of your account to "
                + mask(newEmail)
                + ". If it wasn't you, change your password.\n\n"
                + "The Fluveny team\n";
    }

    /** O aviso ao endereço antigo; a troca já valeu, então a falha só vai para o log. */
    private void notifyChanged(User old, String newEmail) {
        try {
            this.mail.enqueue(
                    old.email(),
                    CHANGED_SUBJECT,
                    changedText(old.name(), newEmail),
                    MailPriority.HIGH);
        } catch (RuntimeException ex) {
            // Sem a mensagem original: ela pode trazer o e-mail.
            log.error("Falha ao avisar a troca de e-mail ({})", ex.getClass().getName());
        }
    }

    private static String changedText(String name, String newEmail) {
        return greeting(name)
                + "\n\n"
                + "The email of your Fluveny account was changed to "
                + mask(newEmail)
                + ". This address won't get the account emails anymore.\n\n"
                + "If it wasn't you, contact the Fluveny team.\n\n"
                + "The Fluveny team\n";
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
