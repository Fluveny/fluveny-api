package com.fluveny.fluveny_backend.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import org.junit.jupiter.api.Test;

/** Todo Tipo registrado tem a sua regra de conclusão, mesmo vazia (história 1.13). */
class LearningActivityTypesTest {

    @Test
    void everyRegisteredTypeHasACompletionRule() {
        assertThat(LearningActivityTypes.completionTypes())
                .containsExactlyInAnyOrderElementsOf(
                        new LearningActivityTypes()
                                .activityTypes().stream().map(ActivityTypeSupport::type).toList());
    }
}
