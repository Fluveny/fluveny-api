package com.fluveny.fluveny_backend.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaRequestDTO extends LoginRequestDTO {

    @NotNull(message = "CAPTCHA response is required")
    @NotBlank(message = "CAPTCHA response cannot be blank")
    private String captchaResponse;
}