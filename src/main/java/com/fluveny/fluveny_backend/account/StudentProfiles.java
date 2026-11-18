package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import org.springframework.stereotype.Service;

/** O perfil mínimo do Aluno (história 2.1): leitura e pronomes. O dono é sempre o da sessão. */
@Service
public class StudentProfiles {

    private final UserStore users;

    StudentProfiles(UserStore users) {
        this.users = users;
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

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail());
    }

    private static StudentProfile toProfile(User user) {
        return new StudentProfile(user.name(), user.email(), user.pronounSet());
    }
}
