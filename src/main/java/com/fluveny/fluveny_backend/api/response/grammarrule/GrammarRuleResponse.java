package com.fluveny.fluveny_backend.api.response.grammarrule;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;


@Schema(name = "GrammarRuleResponse")
@AllArgsConstructor
public class GrammarRuleResponse extends ApiResponseFormat<GrammarRuleResponseDTO> {
}