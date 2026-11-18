package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.account.StudentProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * A conta criada e, só no Android, o token da sessão (história 2.1). No web o token vai só no
 * cookie.
 *
 * @param sessionToken só com {@code X-Fluveny-Client: android}; o App guarda no {@code
 *     expo-secure-store} e envia como {@code Authorization: Bearer}
 */
@Schema(name = "SignupResponse")
record SignupResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) StudentProfile student,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description =
                                "Só no Android (X-Fluveny-Client: android). No web, o token vai"
                                        + " só no cookie fluveny_session.")
                @JsonInclude(JsonInclude.Include.NON_NULL)
                String sessionToken) {

    /** Nunca o token. */
    @Override
    public String toString() {
        return "SignupResponse[***]";
    }
}
