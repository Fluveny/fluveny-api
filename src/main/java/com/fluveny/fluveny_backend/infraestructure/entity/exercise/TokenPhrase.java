package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fluveny.fluveny_backend.infraestructure.enums.TokenPhraseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GapItem.class, name = "GAP"),
        @JsonSubTypes.Type(value = TextItem.class, name = "TEXT")
})
@JsonIgnoreProperties({"type"})
public abstract class TokenPhrase {
    private TokenPhraseEnum type;
}
