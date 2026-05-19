package com.fluveny.fluveny_backend.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private String username;
    private String name;
    private String email;
    private String role;
    private String avatar;
    private String background;
    private Integer level;
    private Long xp;
    private Long maxXp;
    private Boolean soundEnabled;
    private Boolean requiresPasswordReset;
    private Boolean requiresProfileSetup;
}
