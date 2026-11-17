package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Cartão de um bloco dos Cartões de expressões. Não tem segredo. */
@Schema(name = "CardItem")
public record CardItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText text) {

    public CardItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(text, "text");
    }
}
