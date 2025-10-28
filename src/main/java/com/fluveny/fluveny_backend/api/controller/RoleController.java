package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.response.role.RoleResponse;
import com.fluveny.fluveny_backend.business.service.RoleService;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.RoleEntity;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    @Autowired
    private RoleService roleService;

    @Operation(summary = "Get all Roles",
            description = "This endpoint is used to GET all roles",
            tags = {"Role"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All roles fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoleResponse.class)
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
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<RoleEntity>>> getAllRoles () {
        List<RoleEntity> roles = roleService.getAllRoles();
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<RoleEntity>>("Roles found with successfully", roles));
    }

}
