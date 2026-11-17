package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Item do Associar na visão do aluno: o {@link MatchItem} sem os campos secretos. */
@Schema(name = "MatchItemStudentView")
public record MatchItemStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText line) {

    public MatchItemStudentView {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(line, "line");
    }
}
