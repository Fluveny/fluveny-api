package com.fluveny.fluveny_backend.learning.visitor;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A conclusão de uma Atividade sem correção pelo Visitante (história 1.12): a vista de um Tipo
 * expositivo ou a pulada de um Tipo avaliado que aceita pular (a Fala, história 1.15). Vale para as
 * Atividades do Estágio liberado, na conclusão que o Tipo aceita, senão {@code 409
 * COMPLETION_NOT_ALLOWED} (história 1.13). Os expositivos (Diálogo, Cartões de expressões,
 * Expressão Útil destacada) aceitam só {@code VIEWED}; a Fala aceita só {@code SKIPPED} (história
 * 1.15).
 *
 * @param attemptId gerado pelo App; chave de idempotência, como na Tentativa
 * @param studyTimeMs tempo na Atividade, em milissegundos
 */
@Schema(name = "Completion")
public record Completion(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID attemptId,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID deviceId,
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
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull CompletionOutcome outcome,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") @NotNull @PositiveOrZero
                Long studyTimeMs) {}
