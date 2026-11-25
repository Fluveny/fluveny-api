package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * A senha atual do Aluno (história 2.9, FR-5, AD-15): a troca de senha e a conferência que a troca
 * de e-mail também exige. A senha atual errada dá {@code 400 CURRENT_PASSWORD_INVALID}, nunca
 * {@code 401} (o App sairia da sessão). Depois de {@value #MAX_FAILURES} erros da conta em 15
 * minutos, as duas trocas dão {@code 429 TOO_MANY_REQUESTS} com {@code Retry-After} até a janela
 * acabar, com a senha certa ou errada. O contador fica em memória, por instância.
 *
 * <p>Só conta {@code ACTIVE} e {@code STUDENT}. Nem a senha nem o hash vão para o log.
 */
@Service
public class AccountPassword {

    static final int MAX_FAILURES = 5;

    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final Passwords passwords;

    private final Clock clock;

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    AccountPassword(UserStore users, OneTimeTokenStore tokens, Passwords passwords, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clock = clock;
    }

    /**
     * Troca a senha, com a senha atual, e apaga os links de redefinição da conta. Não mexe nas
     * sessões: quem chama ({@code auth}) encerra as outras.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} (senha nova fora da regra), {@code 400
     *     CURRENT_PASSWORD_INVALID} ou {@code 429 TOO_MANY_REQUESTS}
     */
    public void change(String userId, String currentPassword, String newPassword) {
        if (PasswordPolicy.violation(newPassword).isPresent()) {
            throw RegisterStudent.invalid("newPassword", PasswordPolicy.MESSAGE);
        }
        User user = verifyCurrent(userId, currentPassword);
        if (Passwords.normalize(newPassword).equals(Passwords.normalize(currentPassword))) {
            throw RegisterStudent.invalid("newPassword", "Escolha uma senha diferente da atual.");
        }
        String hash = this.passwords.hash(newPassword);
        if (!this.users.replacePasswordHash(user.id(), user.passwordHash(), hash)) {
            // Outra troca ou a desativação no meio: a senha atual não vale mais.
            throw currentPasswordInvalid();
        }
        this.tokens.deleteByUser(user.id(), TokenPurpose.PASSWORD_RESET);
    }

    /**
     * Confere a senha atual da conta, com o limite de erros.
     *
     * @return a conta
     * @throws ApiException {@code 400 CURRENT_PASSWORD_INVALID}, {@code 429 TOO_MANY_REQUESTS} ou
     *     {@code 404 NOT_FOUND} se a conta sumiu ou não é um Aluno ativo
     */
    User verifyCurrent(String userId, String currentPassword) {
        User user =
                this.users
                        .findById(userId)
                        .filter(found ->
                                found.status() == AccountStatus.ACTIVE
                                        && found.role() == Role.STUDENT)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail()));
        long now = this.clock.millis();
        this.failures.values().removeIf(entry -> entry.expired(now));
        // Reserva a tentativa antes de conferir: pedidos em paralelo não passam do limite.
        Failures reserved =
                this.failures.compute(
                        user.id(),
                        (key, existing) ->
                                (existing == null || existing.expired(now))
                                        ? new Failures(now, 1)
                                        : new Failures(existing.start, existing.count + 1));
        if (reserved.count > MAX_FAILURES) {
            throw new RateLimitedException(reserved.retryAfterSeconds(now));
        }
        if (!this.passwords.matches(currentPassword, user.passwordHash())) {
            throw currentPasswordInvalid();
        }
        // A senha certa zera os erros.
        this.failures.remove(user.id());
        return user;
    }

    private static ApiException currentPasswordInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, CURRENT_PASSWORD_INVALID, "A senha atual está incorreta.");
    }

    /** Os erros da conta desde {@code start}, numa janela de 15 minutos. */
    private record Failures(long start, int count) {

        boolean expired(long now) {
            return now - this.start >= FAILURE_WINDOW.toMillis();
        }

        long retryAfterSeconds(long now) {
            long remaining = this.start + FAILURE_WINDOW.toMillis() - now;
            return Math.max(1, (remaining + 999) / 1000);
        }
    }
}
