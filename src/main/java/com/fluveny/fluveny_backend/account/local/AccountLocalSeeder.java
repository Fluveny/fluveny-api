package com.fluveny.fluveny_backend.account.local;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.Passwords;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.AccountIndexes;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import com.fluveny.fluveny_backend.platform.local.LocalSeedPassword;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 *   <li>{@value #WITH_PRONOUNS}, com nome e pronomes {@code SHE}, com o e-mail confirmado;
 *   <li>{@value #WITHOUT_PRONOUNS}, sem nome nem pronomes (vale they/them), com o e-mail por
 *       confirmar (história 2.2);
 *   <li>{@value #DEACTIVATED}, {@code DEACTIVATED}, para o {@code 403 ACCOUNT_DEACTIVATED} do login
 *       (história 2.3).
 * </ul>
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

    /** As versões dos documentos legais da 1.21 no App. */
    static final String TERMS_VERSION = "0.1";

    static final String PRIVACY_VERSION = "0.2";

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 5, 20);

    private static final Logger log = LoggerFactory.getLogger(AccountLocalSeeder.class);

    private final AccountIndexes indexes;

    private final UserStore users;

    private final Passwords passwords;

    private final LocalSeedPassword seedPassword;

    private final Clock clock;

    AccountLocalSeeder(
            AccountIndexes indexes,
            UserStore users,
            Passwords passwords,
            LocalSeedPassword seedPassword,
            Clock clock) {
        this.indexes = indexes;
        this.users = users;
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
                student(WITH_PRONOUNS, hash, "Ana", PronounSet.SHE, true, AccountStatus.ACTIVE));
        this.users.upsertByEmail(
                student(WITHOUT_PRONOUNS, hash, null, null, false, AccountStatus.ACTIVE));
        this.users.upsertByEmail(
                student(DEACTIVATED, hash, "Bia", null, true, AccountStatus.DEACTIVATED));
    }

    private User student(
            String email,
            String hash,
            String name,
            PronounSet pronounSet,
            boolean confirmed,
            AccountStatus status) {
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
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
                now,
                now,
                confirmed ? now : null,
                null,
                null);
    }
}
