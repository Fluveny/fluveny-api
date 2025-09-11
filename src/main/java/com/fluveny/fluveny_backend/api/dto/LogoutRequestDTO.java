package com.fluveny.fluveny_backend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Logout request containing JWT token")
public class LogoutRequestDTO {

    @NotNull(message = "Token is required")
    @NotBlank(message = "Token cannot be blank")
    @Schema(
            description = "JWT token to be invalidated",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            required = true
    )
    private String token;
}