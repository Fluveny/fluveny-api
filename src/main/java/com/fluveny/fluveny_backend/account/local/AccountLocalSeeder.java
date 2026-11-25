package com.fluveny.fluveny_backend.account.local;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.Passwords;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.AccountIndexes;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import com.fluveny.fluveny_backend.platform.local.LocalSeedPassword;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code account} (AD-18, história 2.1): recria os índices de {@code users} e
 * grava, por e-mail, os Alunos adultos de teste, com a senha da {@link LocalSeedPassword}:
 *
 * <ul>
 *   <li>{@value #WITH_PRONOUNS}, com nome, país ({@value #COUNTRY}) e pronomes {@code SHE}, com o
 *       e-mail confirmado;
 *   <li>{@value #WITHOUT_PRONOUNS}, sem nome nem pronomes (vale they/them), com o e-mail por
 *       confirmar (história 2.2);
 *   <li>{@value #DEACTIVATED}, {@code DEACTIVATED}, para o {@code 403 ACCOUNT_DEACTIVATED} do login
 *       (história 2.3);
 *   <li>{@value #OVERDUE}, {@code ACTIVE}, sem confirmar o e-mail e com o cadastro de {@value
 *       #OVERDUE_DAYS} dias atrás, para a faixa {@code emailConfirmationOverdue} (história 2.8). O
 *       {@code createdAt} só é gravado na criação: nos {@code seed} seguintes, a conta fica mais
 *       velha, e a faixa continua.
 * </ul>
 *
 * <p>Cada {@code seed} desfaz a troca de e-mail pendente (história 2.9) e volta nome, país e
 * pronomes aos de cima.
 *
 * <p>Sem {@code LOCAL_SEED_PASSWORD}, ou com ela fora da {@link PasswordPolicy}, os Alunos não são
 * criados, um aviso vai para o log e a API sobe do mesmo jeito.
 */
@Component
@Profile("local")
@Order(150)
class AccountLocalSeeder implements LocalDataSeeder {

    static final String WITH_PRONOUNS = "aluno@fluveny.local";

    static final String WITHOUT_PRONOUNS = "aluno.sem.pronomes@fluveny.local";

    static final String DEACTIVATED = "aluno.desativado@fluveny.local";

    static final String OVERDUE = "aluno.atrasado@fluveny.local";

    static final int OVERDUE_DAYS = 8;

    /** O país de {@value #WITH_PRONOUNS}, para a tela Conta (história 2.9). */
    static final String COUNTRY = "Brazil";

    /** As versões dos documentos legais da 1.21 no App. */
    static final String TERMS_VERSION = "0.1";

    static final String PRIVACY_VERSION = "0.2";

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 5, 20);

    private static final Logger log = LoggerFactory.getLogger(AccountLocalSeeder.class);

    private final AccountIndexes indexes;

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final Passwords passwords;

    private final LocalSeedPassword seedPassword;

    private final Clock clock;

    AccountLocalSeeder(
            AccountIndexes indexes,
            UserStore users,
            OneTimeTokenStore tokens,
            Passwords passwords,
            LocalSeedPassword seedPassword,
            Clock clock) {
        this.indexes = indexes;
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.seedPassword = seedPassword;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "account";
    }

    @Override
    public void seed() {
        this.indexes.ensure();
        String password;
        try {
            password = this.seedPassword.value();
        } catch (IllegalStateException ex) {
            log.warn("Alunos de teste não criados: {}", ex.getMessage());
            return;
        }
        if (PasswordPolicy.violation(password).isPresent()) {
            // Nunca a senha.
            log.warn(
                    "Alunos de teste não criados: LOCAL_SEED_PASSWORD fora da regra de senha. {}",
                    PasswordPolicy.MESSAGE);
            return;
        }
        String hash = this.passwords.hash(password);
        this.users.upsertByEmail(
                student(
                        WITH_PRONOUNS,
                        hash,
                        "Ana",
                        PronounSet.SHE,
                        true,
                        AccountStatus.ACTIVE,
                        Duration.ZERO,
                        COUNTRY));
        this.users.upsertByEmail(
                student(WITHOUT_PRONOUNS, hash, null, null, false, AccountStatus.ACTIVE));
        this.users.upsertByEmail(
                student(DEACTIVATED, hash, "Bia", null, true, AccountStatus.DEACTIVATED));
        this.users.upsertByEmail(
                student(
                        OVERDUE,
                        hash,
                        "Caio",
                        null,
                        false,
                        AccountStatus.ACTIVE,
                        Duration.ofDays(OVERDUE_DAYS),
                        null));
        // A troca de e-mail pendente é desfeita: o link dela não vale mais.
        for (String email : List.of(WITH_PRONOUNS, WITHOUT_PRONOUNS, DEACTIVATED, OVERDUE)) {
            this.users
                    .findByEmail(email)
                    .ifPresent(
                            user -> this.tokens.deleteByUser(user.id(), TokenPurpose.EMAIL_CHANGE));
        }
    }

    private User student(
            String email,
            String hash,
            String name,
            PronounSet pronounSet,
            boolean confirmed,
            AccountStatus status) {
        return student(email, hash, name, pronounSet, confirmed, status, Duration.ZERO, null);
    }

    /**
     * @param age há quanto tempo a conta foi criada
     * @param country o país, ou nulo
     */
    private User student(
            String email,
            String hash,
            String name,
            PronounSet pronounSet,
            boolean confirmed,
            AccountStatus status,
            Duration age,
            String country) {
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant createdAt = now.minus(age);
        return new User(
                null,
                email,
                hash,
                BIRTH_DATE,
                Role.STUDENT,
                status,
                UUID.randomUUID(),
                name,
                pronounSet,
                TERMS_VERSION,
                PRIVACY_VERSION,
                createdAt,
                createdAt,
                confirmed ? now : null,
                null,
                null,
                country);
    }
}
