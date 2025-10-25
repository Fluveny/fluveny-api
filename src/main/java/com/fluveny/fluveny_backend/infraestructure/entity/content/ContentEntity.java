package com.fluveny.fluveny_backend.infraestructure.entity.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseCompletePhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseTranslateRequestDTO;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Setter
@Getter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentExerciseEntity.class, name = "EXERCISE"),
        @JsonSubTypes.Type(value = ContentPresentationEntity.class, name = "PRESENTATION"),
})
public abstract class ContentEntity {
    @EqualsAndHashCode.Include
    private ContentType type;
    @EqualsAndHashCode.Include
    private String id;
}
