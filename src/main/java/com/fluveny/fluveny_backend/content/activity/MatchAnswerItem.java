package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Um par do Associar: a resposta ligada a um item. */
@Schema(name = "MatchAnswerItem")
public record MatchAnswerItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) String responseKey) {

    public MatchAnswerItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(responseKey, "responseKey");
    }
}
