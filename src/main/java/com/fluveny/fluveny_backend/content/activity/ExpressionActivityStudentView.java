package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Visão do aluno da Expressão Útil destacada: igual à {@link ExpressionActivity}, que não tem
 * segredo.
 */
@JsonTypeName(ExpressionActivity.TYPE)
@Schema(name = "ExpressionActivityStudentView")
public record ExpressionActivityStudentView(
        @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = ExpressionActivity.TYPE)
                String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) ContentText title,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        description = "O nome exibido de quem diz a frase.")
                String speaker,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        description =
                                "A frase, com os termos marcados como [[texto|expressionKey]]: ao"
                                        + " menos um em cada variante.")
                ContentText sentence)
        implements ActivityStudentView {

    public ExpressionActivityStudentView {
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(sentence, "sentence");
    }
}
