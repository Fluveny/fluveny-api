package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Visão do aluno da Resposta Livre: a {@link FreeActivity} sem a resposta-modelo (que vem na
 * conclusão {@code VIEWED}) e com o Extra Help sem as respostas aceitas.
 */
@JsonTypeName(FreeActivity.TYPE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FreeActivityStudentView")
public record FreeActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = FreeActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "A pergunta de Alex.")
                ContentText prompt,
        @Schema(requiredMode = RequiredMode.REQUIRED) FreeExtraHelpStudentView extraHelp,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description = "O XP do Extra Help; a Resposta Livre enviada não dá XP.")
                Integer xp)
        implements ActivityStudentView {

    public FreeActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(extraHelp, "extraHelp");
        Objects.requireNonNull(xp, "xp");
    }
}
