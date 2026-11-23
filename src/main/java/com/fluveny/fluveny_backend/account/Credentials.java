package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Conferência de e-mail e senha para o login de {@code auth} (história 2.3, FR-3). E-mail
 * desconhecido, senha errada, conta sem senha utilizável ({@code INVITED}) e, até a 2.11, conta
 * {@code PENDING_GUARDIAN_CONSENT} dão o mesmo {@link Outcome#INVALID}. O e-mail desconhecido roda
 * o BCrypt contra um hash fixo, para o tempo não revelar a conta. Só com a senha certa a conta
 * {@code DEACTIVATED} é revelada ({@link Outcome#DEACTIVATED}).
 *
 * <p>Nem o e-mail nem a senha vão para o log.
 */
@Service
public class Credentials {

    private final UserStore users;

    private final Passwords passwords;

    private final StudentProfiles profiles;

    Credentials(UserStore users, Passwords passwords, StudentProfiles profiles) {
        this.users = users;
        this.passwords = passwords;
        this.profiles = profiles;
    }

    /**
     * Confere as credenciais.
     *
     * @param email como digitado; normalizado como no cadastro
     * @param password como digitada; conferida em NFC e, se falhar, como veio
     */
    public Verification verify(String email, String password) {
        Optional<User> found =
                (email == null)
                        ? Optional.empty()
                        : this.users.findByEmail(RegisterStudent.normalizeEmail(email));
        if (found.isEmpty()) {
            this.passwords.burn(password);
            return Verification.INVALID;
        }
        User user = found.get();
        if (user.status() == AccountStatus.INVITED) {
            // Sem senha utilizável até aceitar o convite (Épico 4).
            this.passwords.burn(password);
            return Verification.INVALID;
        }
        if (!this.passwords.matches(password, user.passwordHash())) {
            return Verification.INVALID;
        }
        return switch (user.status()) {
            case ACTIVE ->
                    new Verification(
                            Outcome.VERIFIED,
                            user.id(),
                            user.role(),
                            this.profiles.toProfile(user));
            case DEACTIVATED -> Verification.DEACTIVATED;
            // O adolescente pendente entra na 2.11; até lá, o 401 genérico.
            case PENDING_GUARDIAN_CONSENT, INVITED -> Verification.INVALID;
        };
    }

    /** O resultado da conferência. */
    public enum Outcome {
        /** Senha certa e conta {@code ACTIVE}. */
        VERIFIED,
        /** Qualquer outra coisa sem a senha certa, inclusive e-mail desconhecido. */
        INVALID,
        /** Senha certa, conta {@code DEACTIVATED}. */
        DEACTIVATED
    }

    /**
     * O resultado e, só em {@link Outcome#VERIFIED}, a conta.
     *
     * @param userId o id da conta, para a sessão
     * @param role o papel da conta
     * @param profile o perfil para a resposta
     */
    public record Verification(Outcome outcome, String userId, Role role, StudentProfile profile) {

        static final Verification INVALID = new Verification(Outcome.INVALID, null, null, null);

        static final Verification DEACTIVATED =
                new Verification(Outcome.DEACTIVATED, null, null, null);

        /** Sem e-mail nem nome. */
        @Override
        public String toString() {
            return "Verification[" + this.outcome + "]";
        }
    }
}
