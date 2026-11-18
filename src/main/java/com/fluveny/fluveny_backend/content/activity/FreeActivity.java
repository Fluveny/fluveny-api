package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Resposta Livre com Extra Help ({@code activities/free.schema.json}, FR-17): o aluno responde à
 * pergunta de Alex com as próprias palavras e compara com a {@code modelAnswer}, sem correção. O
 * Extra Help troca a pergunta pelas lacunas do banco ({@link FreeExtraHelp}). A {@code modelAnswer}
 * é {@code x-fluveny-secret}: sai só na conclusão {@code VIEWED}. O {@code xp} vale só para o Extra
 * Help.
 */
@JsonTypeName(FreeActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FreeActivity")
public record FreeActivity(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = TYPE) String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "A pergunta de Alex.")
                ContentText prompt,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "Uma resposta possível; pode ter {name} e {country}. Sai na"
                                        + " conclusão VIEWED.")
                ContentText modelAnswer,
        @Schema(requiredMode = RequiredMode.REQUIRED) FreeExtraHelp extraHelp,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "O XP do Extra Help; a Resposta Livre enviada não dá XP.")
                Integer xp)
        implements Activity {

    public FreeActivity {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(modelAnswer, "modelAnswer");
        Objects.requireNonNull(extraHelp, "extraHelp");
        Objects.requireNonNull(xp, "xp");
    }

    /** Slug do Tipo: o discriminador {@code type} do schema. */
    public static final String TYPE = "free";
}
