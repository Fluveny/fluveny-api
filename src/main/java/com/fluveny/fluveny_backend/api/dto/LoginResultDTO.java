package com.fluveny.fluveny_backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultDTO {
    private String username;
    private String email;
    private String role;
    private String token;
}

