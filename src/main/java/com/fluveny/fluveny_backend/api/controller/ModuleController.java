package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.*;
import com.fluveny.fluveny_backend.api.mapper.*;
import com.fluveny.fluveny_backend.api.response.module.*;
import com.fluveny.fluveny_backend.business.service.GrammarRuleModuleService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.business.service.SearchStudentService;
import com.fluveny.fluveny_backend.business.service.UserService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Modules and their related entities.
 *
 * This controller provides endpoints to handle CRUD operations for Modules,
 * as well as for Introductions associated with Modules by implementing the
 * IntroductionController interface.
 *
 * Additionally, it manages operations related to GrammarRuleModules that belong
 * to Modules, including fetching and updating grammar rule modules and their contents.
 *
 * By consolidating Module, Introduction, and GrammarRuleModule functionalities,
 * this controller offers a unified API surface for module-related data management,
 * while delegating business logic to appropriate services and maintaining separation of concerns
 * at the service and mapper layers.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modules")
public class ModuleController implements IntroductionController {

    private final ModuleService moduleService;
    private final GrammarRuleModuleService grammarRuleModuleService;
    private final ContentMapper contentMapper;
    private final ModuleMapper moduleMapper;
    private final GrammarRuleModuleMapper grammarRuleModuleMapper;
    private final IntroductionMapper introductionMapper;
    private final ModuleRepository moduleRepository;
    private final TextBlockMapper textBlockMapper;
    private final SearchStudentService searchStudentService;
    private final UserService userService;
    private final ModuleOverviewMapper moduleOverviewMapper;

    @Operation(summary = "Search all modules modules by student",
            description = "This endpoint is responsible for return all modules with students informations",
            tags = {"Module - Student"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Modules found successfully or no modules found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesUserResponse.class)
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
    @GetMapping("/student")
    public ResponseEntity<ApiResponseFormat<Page<ModuleResponseStudentDTO>>> getAllModulesByStudent(
            @RequestParam Integer pageNumber,
            @RequestParam Integer pageSize,
            Authentication authentication
    ){

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No valid session found", HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Page<ModuleResponseStudentDTO> moduleResponseStudentDTOS = moduleService.getAllModuleByStudent(userService.getUserByUsername(userDetails.getUsername()), pageSize, pageNumber);

        if(moduleResponseStudentDTOS.isEmpty()){
            throw new BusinessException("No modules found", HttpStatus.OK);
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<Page<ModuleResponseStudentDTO>>("Modules found successfully", moduleResponseStudentDTOS));

    }

    @Operation(summary = "Search for modules by user",
            description = "This endpoint is responsible for search a modules by user",
            tags = {"Module - Student"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Modules found successfully or no modules found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesUserResponse.class)
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
    @GetMapping("/student/search")
    public ResponseEntity<ApiResponseFormat<Page<ModuleResponseStudentDTO>>> searchByStudent(
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) String moduleName,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<String> grammarRulesId,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<String> levelsId,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<StatusDTOEnum> status,
            @RequestParam Integer pageNumber,
            @RequestParam Integer pageSize,
            Authentication authentication
    ){
        System.out.println();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No valid session found", HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Page<ModuleResponseStudentDTO> moduleResponseStudentDTOS = searchStudentService.searchModuleByStudent(userService.getUserByUsername(userDetails.getUsername()), new SearchModuleStudentDTO(moduleName, grammarRulesId, levelsId, status),pageSize,pageNumber);

        if(moduleResponseStudentDTOS.isEmpty()){
            throw new BusinessException("No modules found", HttpStatus.OK);
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<Page<ModuleResponseStudentDTO>>("Modules found successfully", moduleResponseStudentDTOS));

    }

    @Operation(summary = "Creating a new module",
            description = "This endpoint is responsible for creating a new module")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Module created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponse.class)
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
    @PostMapping
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> createModule(
            @Parameter(description = "Object containing module data", required = true)
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO) {
        ModuleEntity module = moduleService.createModule(moduleMapper.toEntity(moduleRequestDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ModuleResponseDTO>("Module created successfully", moduleMapper.toDTO(module)));
    }

    @Operation(summary = "Update a module by ID",
            description = "This endpoint is used to update a module by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponse.class)
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
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> updateModule(
            @Parameter(description = "ID of the module to be updated", required = true)
            @PathVariable String id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New module data. All fields must be filled, even if unchanged.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ModuleRequestDTO.class))
            )
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO){
        ModuleEntity module = moduleService.updateModule(moduleMapper.toEntity(moduleRequestDTO), id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ModuleResponseDTO>("Module updated successfully", moduleMapper.toDTO(module)));
    }

    @Operation(summary = "Get all contents associated with a specific grammar rule module ID",
            description = "This endpoint is responsible for retrieving all contents of a grammar rule module by its ID")
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
    @GetMapping("/{id}/grammar-rule-modules/{GrammarRuleModuleId}/contents")
    public ResponseEntity<ApiResponseFormat<List<ContentEntity>>> getAllContentsByGrammarRuleModuleId(
            @Parameter(description = "ID of the module that stores the grammar rule", required = true)
            @PathVariable String id,
            @Parameter(description = "ID of the grammar rule module that stores the contents", required = true)
            @PathVariable String GrammarRuleModuleId)
    {
        moduleService.grammarRuleModuleExistsInModule(id, GrammarRuleModuleId);
        List<ContentEntity> contents = grammarRuleModuleService.getContentByGrammarRuleModuleId(GrammarRuleModuleId);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<ContentEntity>>("Contents by Grammar Rule Module has successfully found", contents));
    }

    @Operation(summary = "Get all grammar rule modules associated with a specific module ID",
            description = "This endpoint is responsible for retrieving all grammar rule modules of a module by its ID")
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
    @GetMapping("/{id}/grammar-rule-modules")
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
            description = "This endpoint is used to update a grammar rule module by ID")
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
    @PutMapping("/{id}/grammar-rule-modules/{grammarRuleModuleId}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleModuleEntity>> updateGrammarRuleModuleContentList(
            @Parameter(description = "ID of the module that stores the grammar rule", required = true)
            @PathVariable String id,
            @Parameter(description = "ID of the grammar rule module to be updated", required = true)

            @PathVariable String grammarRuleModuleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New grammar rule module content list. It will update the content display position when loaded.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = GrammarRuleModuleRequestDTO.class))
            )
            @Valid @RequestBody GrammarRuleModuleRequestDTO grammarRuleModuleRequestDTO)
    {

        GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleService.updateGrammarRuleModule(grammarRuleModuleId, grammarRuleModuleMapper.toEntity(grammarRuleModuleRequestDTO));

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<GrammarRuleModuleEntity>("Module updated successfully", grammarRuleModule));
    }

    @Operation(summary = "Update a final challenge module by ID",
            description = "This endpoint is used to update a final challenge by module ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Final Challenge update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FinalChallengeReturnResponse.class)
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
    @PutMapping("/{id}/final-challenge")
    public ResponseEntity<ApiResponseFormat<List<String>>> updateFinalChallengeExerciseList(
            @Parameter(description = "ID of the module that stores the final challenge", required = true)
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New Final Challenge exercise list. It will update the content display position when loaded.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = FinalChallengeRequestDTO.class))
            )
            @Valid @RequestBody FinalChallengeRequestDTO finalChallengeRequestDTO)
    {
        List<String> exerciseList = moduleService.updateFinalChallenge(finalChallengeRequestDTO, id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<String>>("Final Challenge updated successfully", exerciseList));
    }

    @Operation(summary = "Get a Exercises in final challenge module by ID",
            description = "This endpoint is used to get a exercises in final challenge by module ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise in Final Challenge found successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FinalChallengeReturnResponse.class)
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
    @GetMapping("/{id}/final-challenge")
    public ResponseEntity<ApiResponseFormat<List<String>>> getAllExercisesInFinalChallenge(
            @Parameter(description = "ID of the module that stores the final challenge", required = true)
            @PathVariable String id)
    {
        ModuleEntity module = moduleService.getModuleById(id);
        if(module.getFinalChallenge().isEmpty()){
            throw new BusinessException("No Exercises was found for this Final Challenge.", HttpStatus.OK);
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<String>>("Exercise in Final Challenge found successfully", module.getFinalChallenge()));
    }
  
    @Operation(summary = "Get all modules",
            description = "This endpoint is used to GET all module")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All modules fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Modules not found",
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
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<ModuleResponseDTO>>> getAllModules(){
        List<ModuleResponseDTO> modulesDTO = moduleService.getAllModules()
                .stream()
                .map(moduleMapper::toDTO)
                .toList();

        if (modulesDTO.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("No modules found", new ArrayList<>()));
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<ModuleResponseDTO>>("Modules found with successfully", modulesDTO));
    }

    @Operation(summary = "Get module by ID",
            description = "This endpoint is used to GET module by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
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
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> getModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id){

            ModuleResponseDTO moduleDTO = moduleMapper.toDTO(moduleService.getModuleById(id));
            if (moduleDTO == null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("A module with that id was not found", null));
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ModuleResponseDTO>("Modules find with successfully", moduleDTO));

    }

    @Operation(summary = "Delete module by ID",
            description = "This endpoint is used to DELETE module by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
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
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> deleteModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id){
        ModuleEntity module = moduleService.deleteModule(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("A module was delete with success", moduleMapper.toDTO(module)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> getIntroductionByEntityId(@PathVariable String id){
        TextBlockEntity introduction = moduleService.getIntroductionByEntityId(id);
        if (introduction == null) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("No Introduction find for this module", null));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<IntroductionResponseDTO>("Introduction was found", introductionMapper.toDTO(textBlockMapper.toDTO(introduction), id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> updateIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO){
        TextBlockEntity introduction = moduleService.updateIntroduction(id, introductionMapper.toEntity(introductionRequestDTO));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was updated", introductionMapper.toDTO(textBlockMapper.toDTO(introduction), id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> createIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO) {
        TextBlockEntity introduction = moduleService.createIntroduction(id, introductionMapper.toEntity(introductionRequestDTO));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was created", introductionMapper.toDTO(textBlockMapper.toDTO(introduction), id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> deleteIntroduction(@PathVariable String id){
        moduleService.deleteIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was deleted", null));
    }

    @Operation(summary = "Get module overview for student",
            description = "This endpoint returns complete module overview information for the student page")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module overview fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleOverviewResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id}/overview")
    public ResponseEntity<ApiResponseFormat<ModuleOverviewDTO>> getModuleOverview(
            @Parameter(description = "ID of the module", required = true)
            @PathVariable String id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No valid session found", HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        ModuleOverviewDTO moduleOverview = moduleService.getModuleOverview(id, userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Module overview retrieved successfully", moduleOverview));
    }

}
