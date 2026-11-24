package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.StudentIdentities;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.events.AfterCommit;
import com.fluveny.fluveny_backend.platform.events.TransientTransactions;
import io.sentry.Sentry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A migração do Visitante pedida pelo App depois do cadastro (história 2.11, AD-10, NFR20): a mesma
 * {@link VisitorMigration} do cadastro (2.6), numa transação própria e com a retentativa dos erros
 * transitórios. Repetir é idempotente, porque os {@code nonce} já foram consumidos. Conta no limite
 * de escritas do Aluno. Se ao menos um recibo migrou, o {@code VISITOR_MIGRATED} sai depois do
 * commit.
 */
@Service
class StudentVisitorMigrations {

    private static final Logger log = LoggerFactory.getLogger(StudentVisitorMigrations.class);

    private final VisitorMigration migration;

    private final StudentWriteLimit limit;

    private final StudentIdentities identities;

    private final MetricsPublisher metrics;

    private final TransactionTemplate transaction;

    StudentVisitorMigrations(
            VisitorMigration migration,
            StudentWriteLimit limit,
            StudentIdentities identities,
            MetricsPublisher metrics,
            PlatformTransactionManager transactionManager) {
        this.migration = migration;
        this.limit = limit;
        this.identities = identities;
        this.metrics = metrics;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    VisitorMigration.Result migrate(String userId, UUID deviceId, List<String> receipts) {
        this.limit.acquire(userId);
        VisitorMigration.Result result =
                TransientTransactions.run(
                        "Migração do Visitante",
                        () ->
                                this.transaction.execute(status ->
                                        this.migration.migrate(userId, deviceId, receipts)));
        if (result.migrated() > 0) {
            UUID metricsId = this.identities.get(userId).metricsId();
            AfterCommit.run(() -> publishMigrated(metricsId));
        }
        return result;
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
