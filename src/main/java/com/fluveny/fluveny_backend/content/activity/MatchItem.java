package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Item do Associar: uma fala de Alex e a resposta certa. {@code correctResponseKey} e {@code
 * justification} são {@code x-fluveny-secret}; a justificativa é opcional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MatchItem")
public record MatchItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText line,
        @Schema(requiredMode = RequiredMode.REQUIRED) String correctResponseKey,
        ContentText justification) {

    public MatchItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(correctResponseKey, "correctResponseKey");
    }
}
