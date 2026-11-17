package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * Answer da Fala ({@code $defs.answer} de {@code speech.schema.json}): a transcrição do que o aluno
 * disse, com as Variáveis do Aluno já trocadas pelos tokens ({@code {name}}) pelo App (AD-10). No
 * {@code expected}, é a frase-alvo crua. A transcrição não é gravada.
 */
@JsonTypeName(SpeechActivity.TYPE)
@Schema(name = "SpeechAnswer")
public record SpeechAnswer(
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = SpeechActivity.TYPE)
                String type,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        maxLength = 500,
                        description =
                                "A transcrição, com as Variáveis do Aluno trocadas pelos tokens"
                                        + " ({name}). Em branco é 400 INVALID_ANSWER.")
                String transcript)
        implements Answer {

    public SpeechAnswer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(transcript, "transcript");
    }
}
