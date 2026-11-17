package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Cartões de expressões ({@code activities/cards.schema.json}, FR-14): Tipo expositivo, sem Answer,
 * sem segredo e sem XP. A conclusão é {@code VIEWED}.
 */
@JsonTypeName(CardsActivity.TYPE)
@Schema(name = "CardsActivity")
public record CardsActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<CardBlock> blocks)
        implements Activity {

    public CardsActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(blocks, "blocks");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "cards";
}
