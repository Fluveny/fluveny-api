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
 * Tentativa e "Ver resposta" do Visitante (história 1.11, AD-7). Anônimas e só para as Atividades
 * do Estágio liberado; fora dele, {@code 401 UNAUTHENTICATED}.
 */
@RestController
@Tag(name = "visitor")
@PublicRoute
@RequestMapping("/api/v1/visitor/attempts")
class VisitorAttemptController {

    private final VisitorAttempts attempts;

    VisitorAttemptController(VisitorAttempts attempts) {
        this.attempts = attempts;
    }

    @Operation(
            operationId = "submitVisitorAttempt",
            description =
                    "Corrige a Tentativa. expected e as justificativas só voltam no Acerto (100%).")
    @PostMapping
    ApiResponse<AttemptResult> attempt(@Valid @RequestBody Attempt attempt) {
        return ApiResponse.of(this.attempts.attempt(attempt));
    }

    @Operation(
            operationId = "revealVisitorAttempt",
            description =
                    "Ver resposta: corrige a mesma Tentativa e devolve o Resultado com expected e"
                            + " as justificativas, sem XP.")
    @PostMapping("/reveal")
    ApiResponse<AttemptResult> reveal(@Valid @RequestBody Attempt attempt) {
        return ApiResponse.of(this.attempts.reveal(attempt));
    }
}
