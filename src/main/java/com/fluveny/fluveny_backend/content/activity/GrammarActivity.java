package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Grammar Time ({@code activities/grammar.schema.json}, FR-15): Tipo expositivo em três passos, as
 * formas já usadas ({@code used}), a regra ({@code rules}) e os exemplos ({@code examples}). Sem
 * Answer, sem segredo e sem XP. A conclusão é {@code VIEWED}. Não tem relação com o {@code
 * GrammarRule} legado (AD-17).
 */
@JsonTypeName(GrammarActivity.TYPE)
@Schema(name = "GrammarActivity")
public record GrammarActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeSentence> used,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeRule> rules,
        @ArraySchema(minItems = 1, schema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @Schema(requiredMode = RequiredMode.REQUIRED)
                List<GrammarTimeExample> examples)
        implements Activity {

    public GrammarActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(used, "used");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(examples, "examples");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "grammar";
}
