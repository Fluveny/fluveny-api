package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Associar completo ({@code activities/match.schema.json}, FR-20), "Match the Speech Bubbles": o
 * aluno liga cada fala de Alex a uma resposta, em pares únicos. Tem o gabarito e a justificativa de
 * cada item. As respostas que nenhum item usa são os distratores.
 */
@JsonTypeName(MatchActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MatchActivity")
public record MatchActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MatchItem> items,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MatchResponse> responses,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements Activity {

    public MatchActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(responses, "responses");
        Objects.requireNonNull(xp, "xp");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "match";
}
