package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.events.AfterCommit;
import com.fluveny.fluveny_backend.platform.events.StudentRegistered;
import io.sentry.Sentry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Trata o {@link StudentRegistered} (história 2.6, AD-3): na mesma thread e na mesma transação do
 * cadastro, migra o Progresso do Visitante quando o evento traz os recibos. Uma falha de gravação
 * lança e desfaz o cadastro inteiro. Se ao menos um recibo migrou, o {@code VISITOR_MIGRATED} sai
 * depois do commit; uma falha ao publicar vai para o log e o Sentry e não muda a resposta.
 */
@Component
class VisitorMigrationListener {

    private static final Logger log = LoggerFactory.getLogger(VisitorMigrationListener.class);

    private final VisitorMigration migration;

    private final MetricsPublisher metrics;

    VisitorMigrationListener(VisitorMigration migration, MetricsPublisher metrics) {
        this.migration = migration;
        this.metrics = metrics;
    }

    @EventListener
    void on(StudentRegistered registered) {
        if (registered.visitor() == null) {
            return;
        }
        VisitorMigration.Result result =
                this.migration.migrate(
                        registered.userId(),
                        registered.visitor().deviceId(),
                        registered.visitor().receipts());
        if (result.migrated() > 0) {
            UUID metricsId = registered.metricsId();
            AfterCommit.run(() -> publishMigrated(metricsId));
        }
    }

    private void publishMigrated(UUID metricsId) {
        try {
            this.metrics.visitorMigrated(metricsId);
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar VISITOR_MIGRATED", ex);
            Sentry.captureException(ex);
        }
    }
}
