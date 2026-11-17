package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Resposta do Associar, ligável a uma fala. O texto pode ter {@code {name}}; o Answer leva só a
 * {@code responseKey} (AD-10). Não tem segredo.
 */
@Schema(name = "MatchResponse")
public record MatchResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) String responseKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText text) {

    public MatchResponse {
        Objects.requireNonNull(responseKey, "responseKey");
        Objects.requireNonNull(text, "text");
    }
}
