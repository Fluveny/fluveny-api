package com.fluveny.fluveny_backend.api.response.role;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.RoleEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "RoleReponse")
@AllArgsConstructor
public class RoleResponse extends ApiResponseFormat<RoleEntity> {
}
