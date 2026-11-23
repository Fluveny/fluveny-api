package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Cadastro de Aluno adulto (história 2.1, FR-2, AD-13). Confere senha e idade, grava a conta em
 * {@code users} já {@code ACTIVE}, publica o {@code STUDENT_REGISTERED} e emite a confirmação de
 * e-mail (história 2.2). O e-mail repetido esbarra no índice único, inclusive em dois cadastros ao
 * mesmo tempo.
 *
 * <p>Sem transação: a conta é gravada primeiro, e quem chama ({@code auth}) cria a sessão depois.
 * Uma falha ao publicar o evento ou ao emitir a confirmação vai para o log e o Sentry e não desfaz
 * a conta; o Aluno pede outro link pelo reenvio.
 */
@Service
public class RegisterStudent {

    /** O fuso da idade: o dia do aniversário é o do Brasil, não o do UTC. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** Menor que isso é recusado {@code [ASSUMPTION]}. */
    static final int MINIMUM_AGE = 12;

    /** De {@link #MINIMUM_AGE} até isso, só com o responsável (história 2.11). */
    static final int ADULT_AGE = 18;

    static final LocalDate OLDEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private static final Logger log = LoggerFactory.getLogger(RegisterStudent.class);

    private final UserStore users;

    private final Passwords passwords;

    private final MetricsPublisher metrics;

    private final EmailConfirmation confirmation;

    private final StudentProfiles profiles;

    private final Clock clock;

    RegisterStudent(
            UserStore users,
            Passwords passwords,
            MetricsPublisher metrics,
            EmailConfirmation confirmation,
            StudentProfiles profiles,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.metrics = metrics;
        this.confirmation = confirmation;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * Cria a conta.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} (senha, data de nascimento), {@code 422
     *     AGE_BELOW_MINIMUM}, {@code 422 GUARDIAN_CONSENT_REQUIRED} ou {@code 409
     *     EMAIL_ALREADY_REGISTERED}; em todos, nada é gravado
     */
    public RegisteredStudent register(NewStudent student) {
        if (PasswordPolicy.violation(student.password()).isPresent()) {
            throw invalid("password", PasswordPolicy.MESSAGE);
        }
        checkAge(student.birthDate());
        String email = normalizeEmail(student.email());
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
        User user =
                new User(
                        null,
                        email,
                        this.passwords.hash(student.password()),
                        student.birthDate(),
                        Role.STUDENT,
                        AccountStatus.ACTIVE,
                        UUID.randomUUID(),
                        blankToNull(student.name()),
                        null,
                        student.termsVersion().strip(),
                        student.privacyVersion().strip(),
                        now,
                        now,
                        null,
                        null,
                        null);
        String userId =
                this.users
                        .insert(user)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.CONFLICT,
                                        "EMAIL_ALREADY_REGISTERED",
                                        "Este e-mail já tem uma conta. Entre ou recupere a"
                                                + " senha."));
        publishRegistered(user.metricsId());
        issueConfirmation(userId, email, user.name());
        // A conta acabou de nascer: a faixa do e-mail não confirmado sai false.
        return new RegisteredStudent(userId, Role.STUDENT, this.profiles.toProfile(user));
    }

    /** {@code trim} e minúsculas: a chave única de {@code users}. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void checkAge(LocalDate birthDate) {
        LocalDate today = LocalDate.now(this.clock.withZone(ZONE));
        if (birthDate == null) {
            throw invalid("birthDate", "Informe a data de nascimento.");
        }
        if (birthDate.isAfter(today) || birthDate.isBefore(OLDEST_BIRTH_DATE)) {
            throw invalid("birthDate", "Confira a data de nascimento.");
        }
        int age = Period.between(birthDate, today).getYears();
        if (age < MINIMUM_AGE) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "AGE_BELOW_MINIMUM",
                    "O Fluveny é para quem tem 12 anos ou mais.");
        }
        if (age < ADULT_AGE) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "GUARDIAN_CONSENT_REQUIRED",
                    "Quem tem menos de 18 anos precisa da autorização de um responsável.");
        }
    }

    private void publishRegistered(UUID metricsId) {
        try {
            this.metrics.studentRegistered(metricsId, false);
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar STUDENT_REGISTERED", ex);
            Sentry.captureException(ex);
        }
    }

    private void issueConfirmation(String userId, String email, String name) {
        try {
            this.confirmation.issue(userId, email, name);
        } catch (RuntimeException ex) {
            // Sem a mensagem original: ela pode trazer o e-mail.
            log.error("Falha ao emitir a confirmação de e-mail ({})", ex.getClass().getName());
            IllegalStateException sanitized =
                    new IllegalStateException(
                            "Falha ao emitir a confirmação de e-mail ("
                                    + ex.getClass().getName()
                                    + ")");
            sanitized.setStackTrace(ex.getStackTrace());
            Sentry.captureException(sanitized);
        }
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_FAILED.status(),
                ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.detail(),
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
