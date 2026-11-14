package com.fluveny.fluveny_backend.api.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsRequestDTO {

    @NotNull(message = "soundEnabled is required")
    private Boolean soundEnabled;
}
