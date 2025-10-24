package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class GapItem extends TokenPhrase{
    private List<String> words;
    private String justification;
}
