package com.fluveny.fluveny_backend.api.dto.finalchallenge;

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
public class FinalChallengeResponseDTO {
    public List<String> exerciseList = new ArrayList<>();
}
