package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Item das Lacunas na visão do aluno: o {@link GapsItem} sem os campos secretos. */
@Schema(name = "GapsItemStudentView")
public record GapsItemStudentView(@Schema(requiredMode = RequiredMode.REQUIRED) String itemKey) {

    public GapsItemStudentView {
        Objects.requireNonNull(itemKey, "itemKey");
    }
}
