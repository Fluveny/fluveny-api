package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Escolha completa ({@code activities/choice.schema.json}, FR-18), com o gabarito e a justificativa
 * de cada item.
 */
@JsonTypeName(ChoiceActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ChoiceActivity")
public record ChoiceActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ChoiceItem> items,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements Activity {

    public ChoiceActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(xp, "xp");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "choice";
}
