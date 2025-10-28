package com.fluveny.fluveny_backend.api.response.grammarrule;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

import java.util.List;

@Schema(name = "GrammarRulesResponse")
@AllArgsConstructor
public class GrammarRulesResponse extends ApiResponseFormat<List<GrammarRuleResponseDTO>> {
    public GrammarRulesResponse(String message, List<GrammarRuleResponseDTO> data) {
        super(message, data);
    }
}