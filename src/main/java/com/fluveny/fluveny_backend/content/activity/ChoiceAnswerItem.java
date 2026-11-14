package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** A opção escolhida num item da Escolha. */
@Schema(name = "ChoiceAnswerItem")
public record ChoiceAnswerItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) String optionKey) {

    public ChoiceAnswerItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(optionKey, "optionKey");
    }
}
