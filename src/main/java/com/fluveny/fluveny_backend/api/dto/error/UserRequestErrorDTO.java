package com.fluveny.fluveny_backend.api.dto.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestErrorDTO {
    private ErrorResponseDTO error = new ErrorResponseDTO();
}
