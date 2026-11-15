package com.fluveny.fluveny_backend.content.web;

import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Publicação de Versões de Unidade (AD-4, história 1.7). Só o job do GitHub Actions chama, com o
 * token de deploy do ambiente ({@code Role.DEPLOY}).
 */
@RestController
@Tag(name = "content-admin")
@RequiresRole(Role.DEPLOY)
@RequestMapping("/api/v1/admin/content/units/{unitKey}/versions")
class AdminUnitVersionController {

    private final PublishUnitVersion publisher;

    AdminUnitVersionController(PublishUnitVersion publisher) {
        this.publisher = publisher;
    }

    /**
     * {@code 201} quando a versão é nova, {@code 200} quando ela já existia (nada novo é gravado).
     * Com {@code publish=false} a versão é gravada e o ponteiro não se move.
     */
    @Operation(
            operationId = "publishUnitVersion",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    BundleDocs.UnitBundle.class))),
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "A versão já existia",
                        content =
                                @Content(schema = @Schema(implementation = PublicationBody.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "201",
                        description = "Versão nova gravada",
                        content =
                                @Content(schema = @Schema(implementation = PublicationBody.class)))
            })
    @PostMapping
    ResponseEntity<ApiResponse<Publication>> publish(
            @PathVariable String unitKey,
            @RequestParam(defaultValue = "true") boolean publish,
            @RequestBody JsonNode bundle) {
        PublishUnitVersion.Result result =
                this.publisher.publish(unitKey, bundle, publish, TypeCheck.ENFORCE);
        Publication body =
                new Publication(result.versionId(), result.created(), result.published());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(body));
    }

    /** Metadados da versão (o registro de hashes da homologação, Decisão 3); {@code 404} se não. */
    @Operation(operationId = "getUnitVersion")
    @GetMapping("/{versionId}")
    ApiResponse<Version> version(@PathVariable String unitKey, @PathVariable String versionId) {
        return this.publisher
                .version(unitKey, versionId)
                .map(info ->
                        ApiResponse.of(
                                new Version(
                                        info.versionId(),
                                        info.unitKey(),
                                        info.schemaVersion(),
                                        info.order(),
                                        info.createdAt(),
                                        info.published())))
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "UNIT_VERSION_NOT_FOUND",
                                "A versão não existe nesta Unidade."));
    }

    @Schema(name = "UnitVersionPublication")
    record Publication(
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean created,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean published) {}

    /** O envelope da publicação, só para o OpenAPI (o {@code ResponseEntity} esconde o tipo). */
    @Schema(name = "ApiResponseUnitVersionPublication")
    record PublicationBody(
            @Schema(requiredMode = RequiredMode.REQUIRED) Publication data, String message) {}

    @Schema(name = "UnitVersionInfo")
    record Version(
            @Schema(requiredMode = RequiredMode.REQUIRED) String versionId,
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) int schemaVersion,
            @Schema(requiredMode = RequiredMode.REQUIRED) int order,
            @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean published) {}
}
