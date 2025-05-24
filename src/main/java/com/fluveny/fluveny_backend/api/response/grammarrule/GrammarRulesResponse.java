package com.fluveny.fluveny_backend.api.response.grammarrule;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "GrammarRulesResponse")
public class GrammarRulesResponse extends ApiResponseFormat<List<GrammarRuleResponseDTO>> {}