package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * O Extra Help da Resposta Livre: as lacunas do banco, no formato do mode {@code bank} das Lacunas
 * ({@link GapsItem}, {@link GapsWord}) e com as mesmas regras de validação.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FreeExtraHelp")
public record FreeExtraHelp(
        @Schema(description = "A instrução do Extra Help, acima das lacunas.") ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "As linhas, na ordem de exibição. Cada ___ (três ou mais _) é"
                                        + " uma lacuna.")
                List<ContentText> lines,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "Um item por lacuna, na ordem dos ___ nas linhas (linha a"
                                        + " linha).")
                List<GapsItem> items,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "O banco de palavras, na ordem do conteúdo, com os distratores;"
                                        + " o App embaralha.")
                List<GapsWord> bank) {

    public FreeExtraHelp {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(bank, "bank");
    }
}
