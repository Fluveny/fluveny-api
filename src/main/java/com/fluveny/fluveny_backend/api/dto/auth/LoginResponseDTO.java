package com.fluveny.fluveny_backend.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
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

    /**
     * Creates a LoginResponseDTO from a LoginResultDTO.
     * This is a factory method used for mapping the internal service DTO
     * to the external API response DTO, omitting sensitive data like the token.
     *
     * @param result The internal DTO returned by the service layer.
     * @return A new LoginResponseDTO instance ready for the API response.
     */
    public static LoginResponseDTO from(LoginResultDTO result) {
        return new LoginResponseDTO(
                result.getUsername(),
                result.getName(),
                result.getEmail(),
                result.getRole(),
                result.getAvatar(),
                result.getBackground(),
                result.getLevel(),
                result.getXp(),
                result.getMaxXp(),
                result.getSoundEnabled(),
                result.getRequiresPasswordReset()
        );
    }
}