package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * A leitura da conta para {@code learning} (história 2.4, AD-3): o {@code metricsId} dos eventos e
 * o pronome do corretor, sem dado pessoal. O {@code userId} vem sempre da sessão de quem chama.
 */
@Service
public class StudentIdentities {

    private final UserStore users;

    StudentIdentities(UserStore users) {
        this.users = users;
    }

    /**
     * @throws ApiException {@code 404 NOT_FOUND} se a conta sumiu entre a sessão e a leitura
     */
    public StudentIdentity get(String userId) {
        return this.users
                .findById(userId)
                .map(user -> new StudentIdentity(user.metricsId(), user.pronounSet()))
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail()));
    }
}
