package com.fluveny.fluveny_backend.api.dto.auth;

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
public class ResetPasswordRequestDTO {
    @NotNull(message = "New password is required")
    @NotBlank(message = "New password cannot be blank")
    private String newPassword;
}
