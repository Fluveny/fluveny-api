package com.fluveny.fluveny_backend.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatorListResponseDTO {
    private String id;
    private String username;
    private String email;
    private String name;
    private Boolean requiresPasswordReset;
    private LocalDateTime lastLoginAt;
    private Boolean isActive;
    private Long modulesCount;
}
