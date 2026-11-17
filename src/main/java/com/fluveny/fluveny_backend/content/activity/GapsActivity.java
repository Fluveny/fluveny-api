package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Lacunas completas ({@code activities/gaps.schema.json}, FR-19): o aluno completa as lacunas
 * ({@code ___}) das linhas, com o banco de palavras ({@code bank}) ou digitando ({@code typed}).
 * Tem as respostas aceitas e a justificativa de cada item. O banco existe só no {@code bank}, com
 * os distratores.
 */
@JsonTypeName(GapsActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "GapsActivity")
public record GapsActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        allowableValues = {MODE_BANK, MODE_TYPED})
                String mode,
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
                        description =
                                "O banco de palavras, só no mode bank (ausente no typed), na"
                                        + " ordem do conteúdo, com os distratores: o App"
                                        + " embaralha.")
                List<GapsWord> bank,
        @Schema(requiredMode = RequiredMode.REQUIRED) Integer xp)
        implements Activity {

    public GapsActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(xp, "xp");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "gaps";

    /** O aluno toca nas palavras do banco. */
    public static final String MODE_BANK = "bank";

    /** O aluno digita cada lacuna. */
    public static final String MODE_TYPED = "typed";
}
