package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Os eventos do App do Visitante (AD-14, história 1.20): valida e grava pelo {@link
 * MetricsPublisher}. O {@code ACTIVITY_STARTED} é conferido contra o conteúdo como a Tentativa
 * (versão ou Atividade inexistente, {@code 404}; Estágio fechado ao Visitante, {@code 401}), e o
 * {@code unitKey} vem do conteúdo. Conta no limite por {@code deviceId}.
 */
@Service
class VisitorEvents {

    private final ContentActivities activities;

    private final MetricsPublisher metrics;

    private final VisitorDeviceLimit deviceLimit;

    VisitorEvents(
            ContentActivities activities,
            MetricsPublisher metrics,
            VisitorDeviceLimit deviceLimit) {
        this.activities = activities;
        this.metrics = metrics;
        this.deviceLimit = deviceLimit;
    }

    void record(VisitorEvent event) {
        switch (event.type()) {
            case VISITOR_SESSION_STARTED -> {
                if (event.versionId() != null || event.activityKey() != null) {
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED,
                            "VISITOR_SESSION_STARTED leva só type e deviceId.");
                }
                this.deviceLimit.acquire(event.deviceId());
                this.metrics.visitorSessionStarted(event.deviceId());
            }
            case ACTIVITY_STARTED -> {
                if (event.versionId() == null || event.activityKey() == null) {
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED,
                            "ACTIVITY_STARTED exige versionId e activityKey.");
                }
                this.deviceLimit.acquire(event.deviceId());
                ContentActivities.Found found =
                        VisitorAttempts.openToVisitor(
                                this.activities, event.versionId(), event.activityKey());
                this.metrics.activityStarted(
                        event.deviceId(),
                        found.unitKey(),
                        found.versionId(),
                        found.activity().activityKey());
            }
        }
    }
}
