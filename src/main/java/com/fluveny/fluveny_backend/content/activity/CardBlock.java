package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Bloco dos Cartões de expressões: um título, um ícone opcional e os cartões. {@code emoji} não é
 * texto do conteúdo: não aceita placeholder nem marcação. Não tem segredo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CardBlock")
public record CardBlock(
        @Schema(requiredMode = RequiredMode.REQUIRED) String blockKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(
                        minLength = 1,
                        maxLength = 16,
                        description =
                                "O ícone do bloco, um emoji. Texto puro, não é texto do conteúdo:"
                                        + " não aceita placeholder nem marcação.")
                String emoji,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<CardItem> items) {

    public CardBlock {
        Objects.requireNonNull(blockKey, "blockKey");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
    }
}
