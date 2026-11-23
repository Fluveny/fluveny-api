package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.StudentIdentities;
import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import org.springframework.stereotype.Service;

/**
 * Os eventos do App do Aluno (AD-14, história 2.4): o {@code ACTIVITY_STARTED}, conferido contra o
 * conteúdo (versão ou Atividade inexistente, {@code 404}; qualquer Estágio), com o {@code
 * metricsId} da conta e o {@code unitKey} do conteúdo. Conta no limite por {@code userId}.
 */
@Service
class StudentEvents {

    private final ContentActivities activities;

    private final StudentIdentities identities;

    private final MetricsPublisher metrics;

    private final StudentWriteLimit limit;

    StudentEvents(
            ContentActivities activities,
            StudentIdentities identities,
            MetricsPublisher metrics,
            StudentWriteLimit limit) {
        this.activities = activities;
        this.identities = identities;
        this.metrics = metrics;
        this.limit = limit;
    }

    void record(String userId, StudentEvent event) {
        this.limit.acquire(userId);
        switch (event.type()) {
            case ACTIVITY_STARTED -> {
                ContentActivities.Found found =
                        this.activities.find(event.versionId(), event.activityKey());
                this.metrics.studentActivityStarted(
                        this.identities.get(userId).metricsId(),
                        found.unitKey(),
                        found.versionId(),
                        found.activity().activityKey());
            }
        }
    }
}
