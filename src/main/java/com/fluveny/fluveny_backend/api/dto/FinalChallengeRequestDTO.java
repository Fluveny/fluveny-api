package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.infraestructure.entity.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
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
public class FinalChallengeRequestDTO {
    public List<String> exerciseList = new ArrayList<>();
}
