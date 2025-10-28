package com.fluveny.fluveny_backend.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRecoveryRequestDTO {

    @NotNull(message = "Username or email is required")
    @NotBlank(message = "Username or email cannot be blank")
    @Size(min = 3, max = 254, message = "Username or email must be between 3 and 254 characters")
    private String usernameOrEmail;
}