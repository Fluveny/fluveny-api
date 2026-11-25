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
 * O perfil do Aluno (histórias 2.1 e 2.9): leitura e troca de nome, país e pronomes. O dono é
 * sempre o da sessão.
 *
 * <p>É a montagem única do {@link StudentProfile}: {@code GET /students/me}, o login ({@link
 * Credentials}) e o cadastro ({@link RegisterStudent}) passam por {@link #toProfile(User)}.
 */
@Service
public class StudentProfiles {

    /** Depois disso sem confirmar o e-mail, o App mostra a faixa (história 2.8). */
    static final Duration EMAIL_CONFIRMATION_GRACE = Duration.ofDays(7);

    /** O tamanho máximo do nome, o mesmo do cadastro. */
    static final int MAX_NAME_LENGTH = 60;

    private final UserStore users;

    private final Countries countries;

    private final Clock clock;

    StudentProfiles(UserStore users, Countries countries, Clock clock) {
        this.users = users;
        this.countries = countries;
        this.clock = clock;
    }

    /** O perfil da conta; {@code 404} se ela sumiu entre a sessão e a leitura. */
    public StudentProfile get(String userId) {
        return toProfile(this.users.findById(userId).orElseThrow(StudentProfiles::notFound));
    }

    /**
     * Grava nome, país e pronomes (história 2.9): a única porta de escrita deles, usada pela tela
     * Conta e pelo Diálogo ({@code learning}). Campo ausente não muda; o valor nulo ou em branco
     * limpa (os pronomes voltam a they/them). O corretor lê os pronomes a cada Tentativa, então a
     * troca vale na Fala seguinte.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} sem nenhum campo, com o nome acima de
     *     {@value #MAX_NAME_LENGTH} caracteres ou com o país fora da lista; nada é gravado
     */
    public StudentProfile updateProfile(String userId, ProfileChange change) {
        if (change == null || change.isEmpty()) {
            throw RegisterStudent.invalid("body", "Informe ao menos um campo.");
        }
        ProfileChange.Value<String> name = null;
        if (change.name() != null) {
            String value = blankToNull(change.name().value());
            if (value != null && value.length() > MAX_NAME_LENGTH) {
                throw RegisterStudent.invalid("name", "Use até 60 caracteres.");
            }
            name = ProfileChange.Value.of(value);
        }
        ProfileChange.Value<String> country = null;
        if (change.country() != null) {
            String value = blankToNull(change.country().value());
            if (value != null && !this.countries.contains(value)) {
                throw RegisterStudent.invalid("country", "Escolha um país da lista.");
            }
            country = ProfileChange.Value.of(value);
        }
        if (!this.users.updateProfile(
                userId, new ProfileChange(name, country, change.pronounSet()))) {
            throw notFound();
        }
        return get(userId);
    }

    /** O perfil da conta, com a faixa calculada no relógio do servidor. */
    StudentProfile toProfile(User user) {
        return new StudentProfile(
                user.name(),
                user.email(),
                user.country(),
                user.pronounSet(),
                user.emailVerifiedAt() != null,
                emailConfirmationOverdue(user, this.clock.instant()),
                user.pendingEmail());
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

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail());
    }
}
