package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import com.fluveny.fluveny_backend.infraestructure.entity.content.ResolvedContent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public abstract class ExerciseEntity implements ResolvedContent {
    @Id
    private String id;
    private final String type = "EXERCISE";
    private String grammarRuleModuleId;
}
