package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.controller.interfaces.IntroductionController;
import com.fluveny.fluveny_backend.api.controller.interfaces.ModuleInterfaceController;
import com.fluveny.fluveny_backend.api.dto.finalchallenge.FinalChallengeRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.*;
import com.fluveny.fluveny_backend.api.dto.module.introduction.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.introduction.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.*;
import com.fluveny.fluveny_backend.api.mapper.module.ModuleMapper;
import com.fluveny.fluveny_backend.api.response.module.*;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.business.service.SearchStudentService;
import com.fluveny.fluveny_backend.business.service.UserService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.StatusDTOEnum;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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
public class ModuleController implements IntroductionController, ModuleInterfaceController {

    private final ModuleService moduleService;
    private final ModuleMapper moduleMapper;
    private final IntroductionMapper introductionMapper;
    private final TextBlockMapper textBlockMapper;
    private final SearchStudentService searchStudentService;
    private final UserService userService;

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

    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> createModule(
            @Parameter(description = "Object containing module data", required = true)
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO) {
        ModuleEntity module = moduleService.createModule(moduleMapper.toEntity(moduleRequestDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ModuleResponseDTO>("Module created successfully", moduleMapper.toDTO(module)));
    }

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

    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> getModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id){

            ModuleResponseDTO moduleDTO = moduleMapper.toDTO(moduleService.getModuleById(id));
            if (moduleDTO == null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("A module with that id was not found", null));
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ModuleResponseDTO>("Modules find with successfully", moduleDTO));

    }

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
