package com.fluveny.fluveny_backend.infraestructure.entity.content;

import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContentExerciseEntity extends ContentEntity {
    @EqualsAndHashCode.Include
    private ExerciseStyle style;
}
