package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Answer do Extra Help da Resposta Livre ({@code $defs.answer} de {@code free.schema.json}): o
 * valor de cada lacuna, como no {@link GapsAnswer}. A Resposta Livre não tem Answer: o texto do
 * aluno nunca vai ao servidor (FR-7, NFR4).
 */
@JsonTypeName(FreeActivity.TYPE)
@Schema(name = "FreeAnswer")
public record FreeAnswer(
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = FreeActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<GapsAnswerItem> items)
        implements Answer {

    public FreeAnswer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(items, "items");
    }
}
