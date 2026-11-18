package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestão dos eventos do App do Visitante (AD-14, história 1.20). Anônima, sob o limite de taxa do
 * Visitante (por IP e por {@code deviceId}); acima dele, {@code 429 TOO_MANY_REQUESTS}.
 */
@RestController
@Tag(name = "visitor")
@PublicRoute
@RequestMapping("/api/v1/visitor")
class VisitorEventController {

    private final VisitorEvents events;

    VisitorEventController(VisitorEvents events) {
        this.events = events;
    }

    @Operation(
            operationId = "recordVisitorEvent",
            description =
                    "Grava um evento do Visitante, sem PII, com a hora do servidor."
                            + " VISITOR_SESSION_STARTED a cada abertura do player pelo"
                            + " EXPERIMENTAR AGORA, só com type e deviceId. ACTIVITY_STARTED a"
                            + " cada abertura de Atividade, com versionId e activityKey: Atividade"
                            + " inexistente dá 404 e Estágio fechado ao Visitante, 401"
                            + " UNAUTHENTICATED. Campo desconhecido dá 400. Responde 202 sem"
                            + " corpo; o App envia sem esperar.")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void record(@Valid @RequestBody VisitorEvent event) {
        this.events.record(event);
    }
}
