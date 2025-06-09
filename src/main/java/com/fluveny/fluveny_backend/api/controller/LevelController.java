package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.response.level.LevelsResponse;
import com.fluveny.fluveny_backend.api.response.module.ModulesReponse;
import com.fluveny.fluveny_backend.business.service.LevelService;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/levels")
public class LevelController {

    private LevelService levelService;

    @Autowired
    public void setLevelService(LevelService levelService) {
        this.levelService = levelService;
    }

    @Operation(summary = "Get all levels",
            description = "This endpoint is used to GET all levels")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All levels fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LevelsResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Levels not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<LevelEntity>>> getAllLevels() {
        List<LevelEntity> levelsResponse = levelService.getAllLevels();
        if(levelsResponse.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<LevelEntity>>("No levels were retrieved", new ArrayList<>()));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<LevelEntity>>("Levels were successfully retrieved", levelsResponse));
    }

}
