package com.fluveny.fluveny_backend.content.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Linha da aba "A regra" do Grammar Time: forma, uso e exemplo. Não tem segredo e não é o {@code
 * GrammarRule} legado (AD-17).
 */
@Schema(name = "GrammarTimeRule")
public record GrammarTimeRule(
        @Schema(requiredMode = RequiredMode.REQUIRED) String ruleKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText form,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText use,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText example) {

    public GrammarTimeRule {
        Objects.requireNonNull(ruleKey, "ruleKey");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(example, "example");
    }
}
