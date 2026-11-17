package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tentativa, "Ver resposta" e conclusão do Visitante (histórias 1.11 e 1.12, AD-7). Anônimas e só
 * para as Atividades do Estágio liberado; fora dele, {@code 401 UNAUTHENTICATED}. Acima do limite
 * de taxa, {@code 429 TOO_MANY_REQUESTS}.
 */
@RestController
@Tag(name = "visitor")
@PublicRoute
@RequestMapping("/api/v1/visitor")
class VisitorAttemptController {

    private final VisitorAttempts attempts;

    VisitorAttemptController(VisitorAttempts attempts) {
        this.attempts = attempts;
    }

    @Operation(
            operationId = "submitVisitorAttempt",
            description =
                    "Corrige a Tentativa. expected e as justificativas só voltam no Acerto (100%)."
                            + " receipt só volta na primeira Tentativa do aparelho na Atividade, e"
                            + " de novo no reenvio do mesmo attemptId.")
    @PostMapping("/attempts")
    ApiResponse<AttemptResult> attempt(@Valid @RequestBody Attempt attempt) {
        return ApiResponse.of(this.attempts.attempt(attempt));
    }

    @Operation(
            operationId = "revealVisitorAttempt",
            description =
                    "Ver resposta: corrige a mesma Tentativa e devolve o Resultado com expected e"
                            + " as justificativas, sem XP e sem receipt. Exige uma Tentativa do"
                            + " aparelho na Atividade (409 ATTEMPT_REQUIRED).")
    @PostMapping("/attempts/reveal")
    ApiResponse<AttemptResult> reveal(@Valid @RequestBody Attempt attempt) {
        return ApiResponse.of(this.attempts.reveal(attempt));
    }

    @Operation(
            operationId = "completeVisitorActivity",
            description =
                    "Conclui uma Atividade sem correção, sem XP. receipt só volta na primeira"
                            + " conclusão ou Tentativa do aparelho na Atividade. Cada Tipo aceita a"
                            + " sua conclusão: hoje os expositivos dialogue, cards e expression,"
                            + " com VIEWED; SKIPPED fica reservado a um Tipo futuro. Fora dela, 409"
                            + " COMPLETION_NOT_ALLOWED.")
    @PostMapping("/completions")
    ApiResponse<CompletionResult> complete(@Valid @RequestBody Completion completion) {
        return ApiResponse.of(this.attempts.complete(completion));
    }
}
