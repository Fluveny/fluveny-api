package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * O perfil mínimo do Aluno (história 2.1): leitura e pronomes. O dono é sempre o da sessão.
 *
 * <p>É a montagem única do {@link StudentProfile}: {@code GET /students/me}, o login ({@link
 * Credentials}) e o cadastro ({@link RegisterStudent}) passam por {@link #toProfile(User)}.
 */
@Service
public class StudentProfiles {

    /** Depois disso sem confirmar o e-mail, o App mostra a faixa (história 2.8). */
    static final Duration EMAIL_CONFIRMATION_GRACE = Duration.ofDays(7);

    private final UserStore users;

    private final Clock clock;

    StudentProfiles(UserStore users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** O perfil da conta; {@code 404} se ela sumiu entre a sessão e a leitura. */
    public StudentProfile get(String userId) {
        return toProfile(this.users.findById(userId).orElseThrow(StudentProfiles::notFound));
    }

    /**
     * Grava os pronomes.
     *
     * @param pronounSet os pronomes; nulo volta ao padrão they/them
     */
    public StudentProfile updatePronouns(String userId, PronounSet pronounSet) {
        if (!this.users.updatePronounSet(userId, pronounSet)) {
            throw notFound();
        }
        return get(userId);
    }

    /** O perfil da conta, com a faixa calculada no relógio do servidor. */
    StudentProfile toProfile(User user) {
        return new StudentProfile(
                user.name(),
                user.email(),
                user.pronounSet(),
                user.emailVerifiedAt() != null,
                emailConfirmationOverdue(user, this.clock.instant()));
    }

    /**
     * Sem confirmar e com {@code createdAt + 7 dias <= now}. Sem {@code createdAt}, nunca (não há
     * de quando contar).
     */
    static boolean emailConfirmationOverdue(User user, Instant now) {
        return user.emailVerifiedAt() == null
                && user.createdAt() != null
                && !user.createdAt().plus(EMAIL_CONFIRMATION_GRACE).isAfter(now);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail());
    }
}
