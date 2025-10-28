package com.fluveny.fluveny_backend.api.controller.grammarRuleModule;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleModuleTinyDTO;
import com.fluveny.fluveny_backend.api.mapper.grammarrulemodule.GrammarRuleModuleMapper;
import com.fluveny.fluveny_backend.api.response.module.ContentsResponse;
import com.fluveny.fluveny_backend.api.response.module.GrammarRuleModulesTinyResponse;
import com.fluveny.fluveny_backend.business.service.ContentManagerService;
import com.fluveny.fluveny_backend.business.service.GrammarRuleModuleService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.enums.ParentOfTheContent;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.content.ContentEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modules/{id}/grammar-rule-modules")
public class GrammarRuleModuleController {

    private final ModuleService moduleService;
    private final GrammarRuleModuleService grammarRuleModuleService;
    private final GrammarRuleModuleMapper grammarRuleModuleMapper;
    private final ContentManagerService contentManagerService;

    @Operation(summary = "Get all contents associated with a specific grammar rule module ID",
            description = "This endpoint is responsible for retrieving all contents of a grammar rule module by its ID",
            tags = {"Grammar Rule Module"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contents were successfully found or no contents were found for the given ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ContentsResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "No gramma rule module with this ID was found or Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ContentsResponse.class)
                    )
            ),
    })
    @GetMapping("/{id_grammarRuleModule}/contents")
    public ResponseEntity<ApiResponseFormat<List<ContentEntity>>> getAllContentsByGrammarRuleModuleId(
            @Parameter(description = "ID of the module that stores the grammar rule", required = true)
            @PathVariable String id,
            @Parameter(description = "ID of the grammar rule module that stores the contents", required = true)
            @PathVariable String id_grammarRuleModule)
    {
        moduleService.grammarRuleModuleExistsInModule(id, id_grammarRuleModule);
        List<ContentEntity> contents = grammarRuleModuleService.getContentByGrammarRuleModuleId(id_grammarRuleModule);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<ContentEntity>>("Contents by Grammar Rule Module has successfully found", contents));
    }

    @Operation(summary = "Get all grammar rule modules associated with a specific module ID",
            description = "This endpoint is responsible for retrieving all grammar rule modules of a module by its ID",
            tags = {"Grammar Rule Module"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule modules were successfully found or no modules were found for the given ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleModulesTinyResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "No module with this ID was found or Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleModulesTinyResponse.class)
                    )
            ),
    })
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<GrammarRuleModuleTinyDTO>>> getAllGrammarRulesModulesByIdModule(
            @Parameter(description = "ID of the module that stores the grammar rule", required = true)
            @PathVariable String id)
    {
        List<GrammarRuleModuleEntity> grammarRulesModules = moduleService.getAllGrammarRulesModulesByIdModule(id);
        List<GrammarRuleModuleTinyDTO> tinyDTO = new ArrayList<>();

        if(grammarRulesModules.isEmpty()){
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<GrammarRuleModuleTinyDTO>>("No grammar rule module was found for this module.", tinyDTO));
        }

        for (GrammarRuleModuleEntity grammarRuleModule : grammarRulesModules) {
            tinyDTO.add(grammarRuleModuleMapper.toTinyDTO(grammarRuleModule));
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<GrammarRuleModuleTinyDTO>>("Grammar rule modules successfully found", tinyDTO));
    }

    @Operation(summary = "Update a grammar rule module by ID",
            description = "This endpoint is used to update a grammar rule module by ID",
            tags = {"Grammar Rule Module"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule module update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleModuleEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PutMapping("/{id_grammarRuleModule}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleModuleEntity>> updateGrammarRuleModuleContentList(
            @Parameter(description = "ID of the module that stores the grammar rule", required = true)
            @PathVariable String id,
            @Parameter(description = "ID of the grammar rule module to be updated", required = true)

            @PathVariable String id_grammarRuleModule,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New grammar rule module content list. It will update the content display position when loaded.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = GrammarRuleModuleRequestDTO.class))
            )
            @Valid @RequestBody GrammarRuleModuleRequestDTO grammarRuleModuleRequestDTO)
    {

        GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleService.updateGrammarRuleModule(id_grammarRuleModule, grammarRuleModuleMapper.toEntity(grammarRuleModuleRequestDTO));

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<GrammarRuleModuleEntity>("Module updated successfully", grammarRuleModule));
    }

    @Operation(summary = "Delete an content",
            description = "This endpoint is used to delete an content from a Grammar Rule Module",
            tags = {"Grammar Rule Module"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Content or Grammar Rule Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @DeleteMapping("/{id_grammarRuleModule}/contents/{id_content}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteContent(

            @PathVariable String id,
            @PathVariable String id_grammarRuleModule,
            @PathVariable String id_content) {

        moduleService.grammarRuleModuleExistsInModule(id, id_grammarRuleModule);
        contentManagerService.deleteContent(id_content, id_grammarRuleModule, ParentOfTheContent.GRAMMAR_RULE_MODULE);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Exercise deleted successfully", null));
    }

}
