package com.fluveny.fluveny_backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaCheckResponseDTO {
    private boolean requiresCaptcha;
    private boolean accountLocked;
    private String message;
    private int failedAttempts;
}