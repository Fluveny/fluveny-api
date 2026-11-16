package com.fluveny.fluveny_backend.learning.visitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O Resultado de um item.
 *
 * @param justification o {@code ContentText} cru do item, só depois de um Acerto ou de "Ver
 *     resposta"; o App renderiza com as variáveis locais
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ItemResult")
public record ItemResult(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) boolean correct,
        ContentText justification) {}
