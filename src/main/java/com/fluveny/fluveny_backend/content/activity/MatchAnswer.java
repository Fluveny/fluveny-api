package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Answer do Associar ({@code $defs.answer} de {@code match.schema.json}): a resposta ligada a cada
 * item, só pelas chaves. O texto da resposta, com o nome do aluno, nunca vai à API (AD-10).
 */
@JsonTypeName(MatchActivity.TYPE)
@Schema(name = "MatchAnswer")
public record MatchAnswer(
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = MatchActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MatchAnswerItem> items)
        implements Answer {

    public MatchAnswer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(items, "items");
    }
}
