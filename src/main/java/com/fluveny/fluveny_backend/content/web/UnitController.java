package com.fluveny.fluveny_backend.content.web;

import com.fluveny.fluveny_backend.content.publication.PublishedUnits;
import com.fluveny.fluveny_backend.content.publication.StudentViews;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas do aluno sobre a versão {@code published} (história 1.7). Públicas e só com {@code
 * *StudentView}: nada aqui leva campo {@code x-fluveny-secret}. O {@code getStage} lê a sessão
 * opcional: a de Aluno abre todos os Estágios publicados (AD-6, história 2.4), sem {@code
 * STAGE_LOCKED}: a sequência vale na Tentativa, no "Ver resposta" e na conclusão (história 2.5).
 */
@RestController
@Tag(name = "units")
@PublicRoute
@RequestMapping("/api/v1/units")
class UnitController {

    private final PublishedUnits units;

    UnitController(PublishedUnits units) {
        this.units = units;
    }

    @Operation(operationId = "listUnits")
    @GetMapping
    ApiResponse<List<StudentViews.UnitListItem>> list() {
        return ApiResponse.of(this.units.list());
    }

    @Operation(operationId = "getUnit")
    @GetMapping("/{unitKey}")
    ApiResponse<StudentViews.Unit> unit(@PathVariable String unitKey) {
        return ApiResponse.of(this.units.unit(unitKey));
    }

    @Operation(
            operationId = "getStage",
            description =
                    "O Estágio na visão do aluno. Sem sessão, só o primeiro Estágio da Unidade de"
                            + " menor order; os demais dão 401 UNAUTHENTICATED. Com sessão de Aluno,"
                            + " todos os Estágios publicados, inclusive os que o painel mostra"
                            + " bloqueados: a sequência (409 STAGE_LOCKED) vale na Tentativa, no Ver"
                            + " resposta e na conclusão.")
    @GetMapping("/{unitKey}/stages/{stageKey}")
    ApiResponse<StudentViews.Stage> stage(
            @PathVariable String unitKey, @PathVariable String stageKey) {
        boolean student =
                SessionPrincipal.find()
                        .map(principal -> principal.role() == Role.STUDENT)
                        .orElse(false);
        return ApiResponse.of(this.units.stage(unitKey, stageKey, student));
    }
}
