package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.activity.Answer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Uma Tentativa (AD-6, AD-7): a resposta a uma Atividade da versão que o aluno viu.
 *
 * @param attemptId gerado pelo App; chave de idempotência da primeira Tentativa (AD-9)
 * @param deviceId o aparelho do Visitante, gerado e guardado pelo App (história 1.12). Opcional
 *     (Decisão 3): sem ele, a Tentativa é corrigida como na 1.11, sem gravar nem gerar recibo
 * @param versionId a Versão de Unidade exibida, {@code sha256:<hex>}
 * @param type o Tipo da Atividade; precisa ser o mesmo da Atividade e do {@code answer}
 * @param studyTimeMs tempo na Atividade, em milissegundos
 */
@Schema(name = "Attempt")
public record Attempt(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID attemptId,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description =
                                "O aparelho do Visitante. Sem ele, a Tentativa é corrigida sem"
                                        + " gravar nem gerar receipt, e o reveal responde 409"
                                        + " ATTEMPT_REQUIRED.")
                UUID deviceId,
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
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") @NotNull @PositiveOrZero
                Long studyTimeMs) {}
