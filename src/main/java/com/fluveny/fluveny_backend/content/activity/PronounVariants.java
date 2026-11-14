package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** As três variantes de pronome de uma frase (AD-8). O código escolhe uma e não conjuga nada. */
@Schema(name = "PronounVariants")
public record PronounVariants(
        @Schema(requiredMode = RequiredMode.REQUIRED) String he,
        @Schema(requiredMode = RequiredMode.REQUIRED) String she,
        @Schema(requiredMode = RequiredMode.REQUIRED) String they) {}
