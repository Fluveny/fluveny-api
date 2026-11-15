package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/** Item da Escolha na visão do aluno: o {@link ChoiceItem} sem os campos secretos. */
@Schema(name = "ChoiceItemStudentView")
public record ChoiceItemStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText question,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ChoiceOption> options) {

    public ChoiceItemStudentView {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(options, "options");
    }
}
