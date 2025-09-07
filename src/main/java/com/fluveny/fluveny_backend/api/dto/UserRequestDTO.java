package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.infraestructure.entity.RoleEntity;
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
public class UserRequestDTO {

    @NotNull(message = "Username is required")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 8, max = 200,
            message = "Username must be between 8 and 200 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9._]+$",
            message = "Username can only contain letters, numbers, dots, and underscores"
    )
    private String username;

    @NotNull(message = "Password is required")
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 200,
            message = "Password must be between 8 and 200 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&_\\-])[A-Za-z\\d@$!%*?&_\\-]+$",
            message = "Password must contain at least one uppercase letter, one number, and one special character"
    )
    private String password;

    @NotNull(message = "Email is required")
    @NotBlank(message = "Email cannot be blank")
    @Size(min = 5, max = 254,
            message = "Email must be between 8 and 200 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "Email must be in a valid format"
    )
    private String email;

}
