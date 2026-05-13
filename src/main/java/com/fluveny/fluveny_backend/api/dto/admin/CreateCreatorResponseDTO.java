package com.fluveny.fluveny_backend.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateCreatorResponseDTO {
    private String username;
    private String email;
    private String generatedPassword;
}
