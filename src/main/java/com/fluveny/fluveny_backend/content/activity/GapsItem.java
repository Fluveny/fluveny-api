package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Item das Lacunas: uma lacuna e as respostas aceitas dela (a primeira vai no {@code expected}).
 * {@code accepted} e {@code justification} são {@code x-fluveny-secret}; a justificativa é
 * opcional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "GapsItem")
public record GapsItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ContentText> accepted,
        ContentText justification) {

    public GapsItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(accepted, "accepted");
    }
}
