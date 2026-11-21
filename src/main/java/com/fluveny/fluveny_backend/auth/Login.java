package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.Credentials;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.StudentProfile;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.RateLimitedException;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Login por e-mail e senha (história 2.3, FR-3, AD-12): confere o atraso progressivo ({@link
 * LoginThrottle}), as credenciais em {@code account} e cria a sessão. Nem o e-mail, nem a senha,
 * nem o IP vão para o log.
 *
 * <p>"Conferir o atraso, conferir a senha e registrar a falha ou o sucesso" roda sob um lock da
 * origem e depois um da conta, sempre nessa ordem (stripes separados, então não há ciclo): uma
 * rajada em paralelo da mesma origem ou conta não passa toda pela conferência antes da primeira
 * falha gravada. O lock vale por instância; no Render há uma só.
 */
@Service
public class Login {

    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    static final String INVALID_CREDENTIALS_DETAIL = "E-mail ou senha incorretos.";

    static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";

    static final String LOGIN_THROTTLED = "LOGIN_THROTTLED";

    /** Quantos locks por tipo de chave. Duas chaves no mesmo stripe só esperam uma pela outra. */
    static final int STRIPES = 256;

    private final ReentrantLock[] originLocks = locks();

    private final ReentrantLock[] accountLocks = locks();

    private final Credentials credentials;

    private final LoginThrottle throttle;

    private final Sessions sessions;

    Login(Credentials credentials, LoginThrottle throttle, Sessions sessions) {
        this.credentials = credentials;
        this.throttle = throttle;
        this.sessions = sessions;
    }

    /**
     * Entra.
     *
     * @param ip o IP da origem ({@code getRemoteAddr()}, já resolvido pelo {@code
     *     ProxySecretFilter})
     * @throws RateLimitedException {@code 429 LOGIN_THROTTLED} com o {@code Retry-After}, antes de
     *     conferir a senha
     * @throws ApiException {@code 401 INVALID_CREDENTIALS} ou {@code 403 ACCOUNT_DEACTIVATED}; em
     *     ambos, sem sessão
     */
    public LoggedIn login(String email, String password, String ip) {
        String normalized = RegisterStudent.normalizeEmail(email);
        ReentrantLock origin = stripe(this.originLocks, LoginThrottle.origin(ip));
        ReentrantLock account = stripe(this.accountLocks, normalized);
        origin.lock();
        try {
            account.lock();
            try {
                return loginLocked(normalized, password, ip);
            } finally {
                account.unlock();
            }
        } finally {
            origin.unlock();
        }
    }

    private LoggedIn loginLocked(String normalized, String password, String ip) {
        long retryAfter = this.throttle.retryAfterSeconds(normalized, ip);
        if (retryAfter > 0) {
            throw new RateLimitedException(
                    LOGIN_THROTTLED,
                    "Muitas tentativas de entrar. Aguarde alguns segundos e tente de novo.",
                    retryAfter);
        }
        Credentials.Verification verification = this.credentials.verify(normalized, password);
        switch (verification.outcome()) {
            case INVALID -> {
                this.throttle.recordFailure(normalized, ip);
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, INVALID_CREDENTIALS_DETAIL);
            }
            case DEACTIVATED ->
                    // A senha conferiu: não é tentativa de adivinhar, e não conta falha.
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            ACCOUNT_DEACTIVATED,
                            "Esta conta está desativada.");
            case VERIFIED -> {
                // Segue abaixo.
            }
        }
        this.throttle.recordSuccess(normalized, ip);
        Sessions.NewSession session =
                this.sessions.create(verification.userId(), verification.role());
        return new LoggedIn(verification.profile(), session);
    }

    private static ReentrantLock[] locks() {
        ReentrantLock[] locks = new ReentrantLock[STRIPES];
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private static ReentrantLock stripe(ReentrantLock[] locks, String key) {
        return locks[Math.floorMod(key.hashCode(), STRIPES)];
    }

    /** O perfil e a sessão nova. */
    public record LoggedIn(StudentProfile profile, Sessions.NewSession session) {

        /** Sem o token nem o e-mail. */
        @Override
        public String toString() {
            return "LoggedIn[***]";
        }
    }
}
