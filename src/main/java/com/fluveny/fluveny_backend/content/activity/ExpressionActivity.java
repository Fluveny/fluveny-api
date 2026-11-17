package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Expressão Útil destacada ({@code activities/expression.schema.json}, FR-14): Tipo expositivo, sem
 * Answer, sem segredo e sem XP. Os termos são os {@code [[texto|expressionKey]]} de {@code
 * sentence}. A conclusão é {@code VIEWED}.
 */
@JsonTypeName(ExpressionActivity.TYPE)
@Schema(name = "ExpressionActivity")
public record ExpressionActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        description = "O nome exibido de quem diz a frase.")
                String speaker,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "A frase, com os termos marcados como [[texto|expressionKey]]: ao"
                                        + " menos um em cada variante.")
                ContentText sentence)
        implements Activity {

    public ExpressionActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(sentence, "sentence");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "expression";
}
