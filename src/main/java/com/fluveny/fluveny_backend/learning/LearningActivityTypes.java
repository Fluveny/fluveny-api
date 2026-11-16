package com.fluveny.fluveny_backend.learning;

import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Os Tipos que o corretor atende, em {@code GET /api/v1/meta} (AD-6, AD-11). O {@code
 * sinceAppVersion} fica nulo até existir a tag do App que renderiza o Tipo; até lá a publicação
 * fora do perfil local recusa o Tipo ({@code 422 UNSUPPORTED_ACTIVITY_TYPE}).
 */
@Component
class LearningActivityTypes implements ActivityTypeSource {

    @Override
    public List<ActivityTypeSupport> activityTypes() {
        return List.of(new ActivityTypeSupport(ChoiceActivity.TYPE, null));
    }
}
