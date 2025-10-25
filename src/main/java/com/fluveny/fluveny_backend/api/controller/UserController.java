package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.auth.UserRequestDTO;
import com.fluveny.fluveny_backend.api.dto.auth.UserResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.UserMapper;
import com.fluveny.fluveny_backend.api.response.user.UserErrorResponse;
import com.fluveny.fluveny_backend.api.response.user.UserResponse;
import com.fluveny.fluveny_backend.business.service.UserService;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Operation(summary = "Creating a new user",
            description = "This endpoint is responsible for creating a new user",
            tags = {"User"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "user created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "User with this username or email already exists or Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserErrorResponse.class)
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
    public ResponseEntity<ApiResponseFormat<UserResponseDTO>> createUser(
            @Parameter(description = "Object containing user data", required = true)
            @Valid @RequestBody UserRequestDTO userRequestDTO
    ){
        UserEntity userEntity = userService.createUser(userMapper.toEntity(userRequestDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<UserResponseDTO>("User created successfully", userMapper.toDTO(userEntity)));
    }
}
