package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.validation.UsernameOrEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotNull(message = "Username is required")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 8, max = 200,
            message = "Username must be between 8 and 200 characters")
    @UsernameOrEmail
    private String usernameOrEmail;

    @NotNull(message = "Password is required")
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 200,
            message = "Password must be between 8 and 200 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&_\\-])[A-Za-z\\d@$!%*?&_\\-]+$",
            message = "Password must contain at least one uppercase letter, one number, and one special character"
    )
    private String password;
}