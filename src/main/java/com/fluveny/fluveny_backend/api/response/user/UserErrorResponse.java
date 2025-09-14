package com.fluveny.fluveny_backend.api.response.user;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.UserResponseDTO;
import com.fluveny.fluveny_backend.api.dto.error.UserRequestErrorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "UserErrorResponse")
@AllArgsConstructor
public class UserErrorResponse extends ApiResponseFormat<UserRequestErrorDTO> {
}
