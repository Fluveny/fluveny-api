package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Answer das Lacunas ({@code $defs.answer} de {@code gaps.schema.json}): o valor de cada lacuna,
 * com as Variáveis do Aluno trocadas pelos tokens ({@code {name}}) pelo App (AD-10). No {@code
 * expected}, a primeira resposta aceita de cada item, crua.
 */
@JsonTypeName(GapsActivity.TYPE)
@Schema(name = "GapsAnswer")
public record GapsAnswer(
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = GapsActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<GapsAnswerItem> items)
        implements Answer {

    public GapsAnswer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(items, "items");
    }
}
