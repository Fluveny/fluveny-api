package com.fluveny.fluveny_backend.api.dto.grammarrulemodule;

import com.fluveny.fluveny_backend.infraestructure.entity.content.ContentEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GrammarRuleModuleResponseDTO {
    public List<ContentEntity> contentList = new ArrayList<ContentEntity>();
}