package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.events.AfterCommit;
import com.fluveny.fluveny_backend.platform.events.StudentRegistered;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de Aluno (histórias 2.1, 2.6 e 2.11, FR-2, FR-7, AD-3, AD-10, AD-13). Confere senha,
 * idade e país, grava a conta em {@code users} já {@code ACTIVE} e publica o evento {@link
 * StudentRegistered}, que {@code learning} trata na mesma thread e na mesma transação, migrando o
 * Progresso do Visitante. O e-mail repetido esbarra no índice único, inclusive em dois cadastros ao
 * mesmo tempo. De 12 a 17 anos, exige o e-mail do responsável: a conta nasce {@code
 * PENDING_GUARDIAN_CONSENT}, o responsável recebe o link depois do commit e o bloco do Visitante é
 * ignorado (história 2.11).
 *
 * <p>Roda numa transação ({@code REQUIRED}): quem chama ({@code auth}) abre a transação, que cobre
 * a conta, a migração e a sessão; qualquer falha desfaz tudo. O {@code STUDENT_REGISTERED} e a
 * confirmação de e-mail (história 2.2) saem só depois do commit; uma falha neles vai para o log e o
 * Sentry e não muda a resposta, e o Aluno pede outro link pelo reenvio.
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

    private final GuardianConsent guardianConsent;

    private final Countries countries;

    private final ApplicationEventPublisher events;

    private final Clock clock;

    RegisterStudent(
            UserStore users,
            Passwords passwords,
            MetricsPublisher metrics,
            EmailConfirmation confirmation,
            StudentProfiles profiles,
            GuardianConsent guardianConsent,
            Countries countries,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.metrics = metrics;
        this.confirmation = confirmation;
        this.profiles = profiles;
        this.guardianConsent = guardianConsent;
        this.countries = countries;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Cria a conta.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} (senha, data de nascimento, país), {@code
     *     422 AGE_BELOW_MINIMUM}, {@code 422 GUARDIAN_CONSENT_REQUIRED} ou {@code 409
     *     EMAIL_ALREADY_REGISTERED}; em todos, nada é gravado
     */
    @Transactional
    public RegisteredStudent register(NewStudent student) {
        return insert(prepare(student));
    }

    /**
     * Grava a conta já conferida por {@link #prepare}, na transação de quem chama: o {@code auth}
     * confere e roda o BCrypt uma vez, fora da transação, e só refaz esta parte se a transação
     * precisar ser repetida.
     *
     * @throws ApiException {@code 409 EMAIL_ALREADY_REGISTERED}; nada é gravado
     */
    @Transactional
    public RegisteredStudent register(PreparedStudent prepared) {
        return insert(prepared);
    }

    /**
     * Confere senha, idade e país e calcula o hash da senha, sem gravar nada e fora de transação.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} (senha, data de nascimento, país), {@code
     *     422 AGE_BELOW_MINIMUM} ou {@code 422 GUARDIAN_CONSENT_REQUIRED} (de 12 a 17 anos sem o
     *     {@code guardianEmail}); {@code guardianEmail} igual ao e-mail do adolescente dá {@code
     *     400 VALIDATION_FAILED}
     */
    public PreparedStudent prepare(NewStudent student) {
        if (PasswordPolicy.violation(student.password()).isPresent()) {
            throw invalid("password", PasswordPolicy.MESSAGE);
        }
        boolean minor = checkAge(student.birthDate());
        String guardianEmail = null;
        if (minor) {
            if (student.guardianEmail() == null || student.guardianEmail().isBlank()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "GUARDIAN_CONSENT_REQUIRED",
                        "Quem tem menos de 18 anos precisa da autorização de um responsável.",
                        Map.of(
                                "errors",
                                List.of(
                                        Map.of(
                                                "field",
                                                "guardianEmail",
                                                "message",
                                                "Informe o e-mail do seu responsável."))));
            }
            guardianEmail = normalizeEmail(student.guardianEmail());
            if (guardianEmail.equals(normalizeEmail(student.email()))) {
                throw invalid("guardianEmail", "Use o e-mail do seu responsável.");
            }
        }
        String country = blankToNull(student.country());
        if (country != null && !this.countries.contains(country)) {
            throw invalid("country", "Escolha um país da lista.");
        }
        return new PreparedStudent(
                student,
                normalizeEmail(student.email()),
                country,
                this.passwords.hash(student.password()),
                guardianEmail);
    }

    private RegisteredStudent insert(PreparedStudent prepared) {
        NewStudent student = prepared.student();
        String email = prepared.email();
        String country = prepared.country();
        String guardianEmail = prepared.guardianEmail();
        boolean pending = guardianEmail != null;
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
        User user =
                new User(
                        null,
                        email,
                        prepared.passwordHash(),
                        student.birthDate(),
                        Role.STUDENT,
                        pending ? AccountStatus.PENDING_GUARDIAN_CONSENT : AccountStatus.ACTIVE,
                        UUID.randomUUID(),
                        blankToNull(student.name()),
                        null,
                        student.termsVersion().strip(),
                        student.privacyVersion().strip(),
                        now,
                        now,
                        null,
                        null,
                        null,
                        country);
        String userId =
                this.users
                        .insert(user)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.CONFLICT,
                                        "EMAIL_ALREADY_REGISTERED",
                                        "Este e-mail já tem uma conta. Entre ou recupere a"
                                                + " senha."));
        if (pending && !this.users.setGuardianEmail(userId, guardianEmail)) {
            // Desfaz o cadastro: conta pendente sem responsável não teria saída.
            throw new IllegalStateException("Conta pendente sem o e-mail do responsável");
        }
        // O adolescente pendente não migra no cadastro: só depois do consentimento (2.11).
        StudentRegistered.Visitor visitor = pending ? null : student.visitor();
        boolean fromVisitor = visitor != null;
        // Os efeitos de fora da transação primeiro, para o STUDENT_REGISTERED sair antes do
        // VISITOR_MIGRATED que a migração registra depois do commit.
        AfterCommit.run(() -> {
            publishRegistered(user.metricsId(), fromVisitor);
            issueConfirmation(userId, email, user.name());
            if (pending) {
                issueGuardianConsent(userId, guardianEmail, user.name());
            }
        });
        this.events.publishEvent(new StudentRegistered(userId, user.metricsId(), visitor));
        // A conta acabou de nascer: a faixa do e-mail não confirmado sai false.
        return new RegisteredStudent(userId, Role.STUDENT, this.profiles.toProfile(user));
    }

    /** {@code trim} e minúsculas: a chave única de {@code users}. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Confere a idade; {@code true} de 12 a 17 anos. */
    private boolean checkAge(LocalDate birthDate) {
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
        return age < ADULT_AGE;
    }

    private void publishRegistered(UUID metricsId, boolean fromVisitor) {
        try {
            this.metrics.studentRegistered(metricsId, fromVisitor);
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar STUDENT_REGISTERED", ex);
            Sentry.captureException(ex);
        }
    }

    private void issueGuardianConsent(String userId, String guardianEmail, String name) {
        try {
            this.guardianConsent.issue(userId, guardianEmail, name);
        } catch (RuntimeException ex) {
            // Sem a mensagem original: ela pode trazer o e-mail. O adolescente pede o reenvio.
            log.error(
                    "Falha ao emitir o consentimento do responsável ({})", ex.getClass().getName());
            IllegalStateException sanitized =
                    new IllegalStateException(
                            "Falha ao emitir o consentimento do responsável ("
                                    + ex.getClass().getName()
                                    + ")");
            sanitized.setStackTrace(ex.getStackTrace());
            Sentry.captureException(sanitized);
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
