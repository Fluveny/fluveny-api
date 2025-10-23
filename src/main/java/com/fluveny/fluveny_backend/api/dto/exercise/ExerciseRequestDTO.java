package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseCompletePhraseResponse;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "style",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExerciseTranslateRequestDTO.class, name = "TRANSLATE"),
        @JsonSubTypes.Type(value = ExerciseConstructorPhraseRequestDTO.class, name = "ORGANIZE"),
        @JsonSubTypes.Type(value = ExerciseCompletePhraseRequestDTO.class, name = "COMPLETE")
})
public abstract class ExerciseRequestDTO {
    private ExerciseStyle style;
}
