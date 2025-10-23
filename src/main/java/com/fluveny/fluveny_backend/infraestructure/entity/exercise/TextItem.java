package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class TextItem extends TokenPhrase {
    private String content;
}
