package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.infrastructure.LearningIndexes;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptPayload;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptVerifier;
import com.fluveny.fluveny_backend.learning.visitor.FirstAttempt;
import com.fluveny.fluveny_backend.learning.visitor.VisitorAttemptStore;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A migração do Progresso do Visitante para a conta (história 2.6, FR-2, FR-7, AD-9, AD-10): cada
 * recibo válido vira um {@link ActivityResult} da conta e consome o {@code nonce} em {@code
 * visitor_attempts}; as conclusões de Estágio cobertas vão para {@code unit_progress}. Exige a
 * transação de quem chama (o cadastro): qualquer falha de gravação desfaz tudo, inclusive os
 * consumos.
 *
 * <p>Um recibo é descartado, sem lançar, se o {@link ReceiptVerifier} o recusa (formato, HMAC ou
 * JSON); se o {@code deviceId} dele não é o do bloco; se o {@code issuedAt} passou de 90 dias (o
 * TTL de {@code visitor_attempts}) ou está mais de 5 minutos no futuro; se a versão ou a Atividade
 * não existem no conteúdo; ou se o {@code nonce} não é o do documento do Visitante ou já foi
 * consumido. Assim, o recibo reemitido depois do TTL e a mesma Atividade repetida na lista ficam
 * com um só.
 *
 * <p>Não publica {@code ACTIVITY_COMPLETED} nem {@code STAGE_COMPLETED}: o Visitante já contou pelo
 * {@code deviceId}. O log leva só as contagens, nunca {@code deviceId}, {@code userId} nem recibo.
 */
@Service
public class VisitorMigration {

    /** Quanto o {@code issuedAt} pode estar à frente do relógio da API. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(VisitorMigration.class);

    private final ReceiptVerifier verifier;

    private final VisitorAttemptStore attempts;

    private final ContentActivities activities;

    private final ActivityResultStore results;

    private final StageSequence sequence;

    private final Clock clock;

    VisitorMigration(
            ReceiptVerifier verifier,
            VisitorAttemptStore attempts,
            ContentActivities activities,
            ActivityResultStore results,
            StageSequence sequence,
            Clock clock) {
        this.verifier = verifier;
        this.attempts = attempts;
        this.activities = activities;
        this.results = results;
        this.sequence = sequence;
        this.clock = clock;
    }

    /**
     * Migra os recibos do aparelho para a conta, que é nova (não tem registro anterior).
     *
     * @return quantos migraram e quantos foram descartados
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result migrate(String userId, UUID deviceId, List<String> receipts) {
        Instant now = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Set<String> units = new LinkedHashSet<>();
        int migrated = 0;
        // O mesmo recibo repetido migraria uma vez só: não vale outro HMAC nem outra ida ao banco.
        for (String receipt : new LinkedHashSet<>(receipts)) {
            Optional<ActivityResult> result = migrateOne(userId, deviceId, receipt, now);
            if (result.isPresent()) {
                this.results.insert(result.get());
                units.add(result.get().unitKey());
                migrated++;
            }
        }
        if (!units.isEmpty()) {
            this.sequence.recordMigratedCompletions(userId, units);
        }
        int discarded = receipts.size() - migrated;
        log.info(
                "Migração do Visitante: {} recibo(s) migrado(s), {} descartado(s)",
                migrated,
                discarded);
        return new Result(migrated, discarded);
    }

    /** O registro do recibo, já com o {@code nonce} consumido; vazio se ele é descartado. */
    private Optional<ActivityResult> migrateOne(
            String userId, UUID deviceId, String receipt, Instant now) {
        ReceiptPayload payload;
        try {
            payload = this.verifier.verify(receipt);
        } catch (ReceiptVerifier.InvalidReceiptException ex) {
            return Optional.empty();
        }
        if (!payload.deviceId().equals(deviceId)
                || payload.issuedAt().isBefore(now.minus(LearningIndexes.VISITOR_ATTEMPTS_TTL))
                || payload.issuedAt().isAfter(now.plus(CLOCK_SKEW))) {
            return Optional.empty();
        }
        ContentActivities.Found found;
        try {
            found = this.activities.find(payload.versionId(), payload.activityKey());
        } catch (ApiException ex) {
            // Só a versão ou a Atividade inexistente descarta; outro erro desfaz a transação.
            if (ErrorCode.NOT_FOUND.name().equals(ex.getCode())) {
                return Optional.empty();
            }
            throw ex;
        }
        if (!found.unitKey().equals(payload.unitKey())) {
            return Optional.empty();
        }
        Optional<FirstAttempt> first = this.attempts.find(deviceId, payload.activityKey());
        if (first.isEmpty()
                || !first.get().nonce().equals(payload.nonce())
                || !this.attempts.consume(deviceId, payload.activityKey(), payload.nonce(), now)) {
            return Optional.empty();
        }
        boolean graded = payload.outcome() == Outcome.GRADED;
        return Optional.of(
                new ActivityResult(
                        userId,
                        found.unitKey(),
                        payload.activityKey(),
                        found.stageKey(),
                        payload.versionId(),
                        found.activity().type(),
                        first.get().attemptId(),
                        payload.outcome(),
                        graded ? payload.resultPct() : null,
                        graded ? payload.xpAwarded() : 0,
                        first.get().studyTimeMs(),
                        first.get().extraHelp(),
                        now));
    }

    /**
     * O resultado da migração, só em contagens.
     *
     * @param migrated os recibos que viraram registro da conta
     * @param discarded os recibos descartados
     */
    public record Result(int migrated, int discarded) {}
}
