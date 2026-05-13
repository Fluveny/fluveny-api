package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.admin.CreateCreatorRequestDTO;
import com.fluveny.fluveny_backend.api.dto.admin.CreateCreatorResponseDTO;
import com.fluveny.fluveny_backend.api.dto.admin.CreatorListResponseDTO;
import com.fluveny.fluveny_backend.business.service.AdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Create Content Creator", description = "Creates a new content creator with an auto-generated password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content creator created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation errors"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/creators")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseFormat<CreateCreatorResponseDTO>> createContentCreator(
            @Valid @RequestBody CreateCreatorRequestDTO requestDTO) {
        
        CreateCreatorResponseDTO responseDTO = adminService.createContentCreator(requestDTO);
        
        return ResponseEntity.ok(new ApiResponseFormat<>("Content creator created successfully", responseDTO));
    }

    @Operation(summary = "List Content Creators", description = "Retrieves a paginated list of all content creators")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/creators")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseFormat<Page<CreatorListResponseDTO>>> getAllContentCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<CreatorListResponseDTO> response = adminService.getAllContentCreators(page, size);
        
        return ResponseEntity.ok(new ApiResponseFormat<>("Content creators retrieved successfully", response));
    }

    @Operation(summary = "Toggle Content Creator Status", description = "Activates or deactivates a content creator account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Content creator not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PatchMapping("/creators/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseFormat<CreatorListResponseDTO>> toggleCreatorStatus(
            @PathVariable("id") String creatorId) {
        
        CreatorListResponseDTO responseDTO = adminService.toggleCreatorStatus(creatorId);
        
        return ResponseEntity.ok(new ApiResponseFormat<>("Status updated successfully", responseDTO));
    }
}

