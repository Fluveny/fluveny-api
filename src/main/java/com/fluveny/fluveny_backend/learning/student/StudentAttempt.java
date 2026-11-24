package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.content.activity.Answer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Uma Tentativa do Aluno (AD-6, AD-7, história 2.4): a do Visitante sem {@code deviceId}. O dono
 * vem só da sessão. Corpo fechado: campo desconhecido (como {@code userId} ou {@code deviceId}) dá
 * {@code 400}.
 *
 * @param attemptId gerado pelo App; chave de idempotência da primeira Tentativa (AD-9)
 * @param versionId a Versão de Unidade exibida, {@code sha256:<hex>}
 * @param type o Tipo da Atividade; precisa ser o mesmo da Atividade e do {@code answer}
 * @param studyTimeMs tempo na Atividade, em milissegundos, até 24 h
 * @param replay {@code true} no refazer (2.7); opcional, padrão {@code false}
 */
@Schema(name = "StudentAttempt", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StudentAttempt(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID attemptId,
        @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^sha256:[0-9a-f]{64}$")
                @NotNull
                @Pattern(regexp = "^sha256:[0-9a-f]{64}$")
                String versionId,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        pattern = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        maxLength = 200)
                @NotNull
                @Size(max = 200)
                @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
                String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank String type,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull @Valid Answer answer,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minimum = "0",
                        maximum = StudentAttempt.MAX_STUDY_TIME)
                @NotNull
                @PositiveOrZero
                @Max(StudentAttempt.MAX_STUDY_TIME_MS)
                Long studyTimeMs,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description =
                                "true só na primeira resposta (e nos reenvios dela) de uma abertura"
                                        + " de Atividade que já tinha registro: o refazer (2.7).")
                Boolean replay) {

    /** Sem o sinal de refazer. */
    public StudentAttempt(
            UUID attemptId,
            String versionId,
            String activityKey,
            String type,
            Answer answer,
            Long studyTimeMs) {
        this(attemptId, versionId, activityKey, type, answer, studyTimeMs, null);
    }

    /** Se é o refazer de uma Atividade já concluída ({@code replay}, padrão {@code false}). */
    boolean isReplay() {
        return Boolean.TRUE.equals(this.replay);
    }

    /** Teto do {@code studyTimeMs}: 24 h, contra valor absurdo. */
    public static final long MAX_STUDY_TIME_MS = 86_400_000L;

    static final String MAX_STUDY_TIME = "86400000";

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }
}
