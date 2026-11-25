package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import com.fluveny.fluveny_backend.platform.events.TransientTransactions;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Limpeza do que a história 2.11 deixou na homologação (história 2.12, decisão de 25/11): o Fluveny
 * passou a ser só para quem tem 18 anos ou mais, e o consentimento do responsável saiu. Roda na
 * partida e é idempotente: (1) cada conta de {@code users} ainda com {@code status:
 * PENDING_GUARDIAN_CONSENT} é apagada pelo mesmo fluxo da exclusão ({@link
 * AccountDeletionRequested}, numa transação por conta); (2) o {@code guardianEmail} sai de quem
 * ainda tiver; (3) saem os tokens {@code GUARDIAN_CONSENT}, os e-mails do consentimento ainda na
 * fila e a coleção {@code guardian_consents}.
 *
 * <p>Lê tudo por consulta crua, porque o status e o propósito não existem mais nos enums. Ligada
 * por {@code fluveny.cleanup.guardian-consent} (padrão {@code true}) e desligada junto dos índices
 * da partida nos testes sem Mongo ({@code fluveny.mongo.indexes-on-startup=false}). Sai numa
 * história futura, depois que a homologação estiver limpa. Nada de id nem e-mail no log.
 */
@Component
public class GuardianConsentCleanup {

    static final String PENDING_STATUS = "PENDING_GUARDIAN_CONSENT";

    static final String TOKEN_PURPOSE = "GUARDIAN_CONSENT";

    static final String CONSENT_SUBJECT = "Autorize o cadastro no Fluveny";

    static final String CONSENTS_COLLECTION = "guardian_consents";

    static final String USERS = "users";

    static final String TOKENS = "one_time_tokens";

    static final String OUTBOX = "mail_outbox";

    private static final Logger log = LoggerFactory.getLogger(GuardianConsentCleanup.class);

    private final MongoTemplate mongo;

    private final ApplicationEventPublisher events;

    private final TransactionTemplate transaction;

    GuardianConsentCleanup(
            MongoTemplate mongo,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.mongo = mongo;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Roda os três passos; rodar de novo não acha nada e não falha. */
    public void run() {
        Query pending = Query.query(Criteria.where("status").is(PENDING_STATUS));
        List<Document> accounts = this.mongo.find(pending, Document.class, USERS);
        for (Document account : accounts) {
            ObjectId id = account.getObjectId("_id");
            try {
                TransientTransactions.run(
                        "Limpeza do consentimento",
                        () -> this.transaction.execute(status -> delete(id)));
            } catch (RuntimeException ex) {
                // Uma conta que falha não derruba a partida nem as outras; só a classe no log.
                log.error(
                        "Falha ao apagar uma conta pendente na limpeza ({})",
                        ex.getClass().getName());
            }
        }
        this.mongo.updateMulti(
                Query.query(Criteria.where("guardianEmail").exists(true)),
                new Update().unset("guardianEmail"),
                USERS);
        this.mongo.remove(Query.query(Criteria.where("purpose").is(TOKEN_PURPOSE)), TOKENS);
        this.mongo.remove(Query.query(Criteria.where("subject").is(CONSENT_SUBJECT)), OUTBOX);
        if (this.mongo.collectionExists(CONSENTS_COLLECTION)) {
            this.mongo.dropCollection(CONSENTS_COLLECTION);
        }
        if (!accounts.isEmpty()) {
            log.info(
                    "Limpeza do consentimento: {} conta(s) pendente(s) apagada(s)",
                    accounts.size());
        }
    }

    private Void delete(ObjectId id) {
        Document account =
                this.mongo.findOne(
                        Query.query(Criteria.where("_id").is(id).and("status").is(PENDING_STATUS)),
                        Document.class,
                        USERS);
        if (account == null) {
            return null;
        }
        UUID metricsId = metricsIdOf(account);
        String email = account.getString("email");
        if (metricsId != null && email != null) {
            this.events.publishEvent(
                    new AccountDeletionRequested(
                            id.toHexString(), metricsId, email, account.getString("pendingEmail")));
        }
        this.mongo.remove(Query.query(Criteria.where("userId").is(id)), TOKENS);
        this.mongo.remove(Query.query(Criteria.where("_id").is(id)), USERS);
        return null;
    }

    /** O {@code metricsId} da conta; nulo se falta ou não é UUID (aí só a conta sai). */
    private static UUID metricsIdOf(Document account) {
        Object value = account.get("metricsId");
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Component
    @ConditionalOnExpression(
            "${fluveny.cleanup.guardian-consent:true} and ${fluveny.mongo.indexes-on-startup:true}")
    static class OnStartup {

        private final GuardianConsentCleanup cleanup;

        OnStartup(GuardianConsentCleanup cleanup) {
            this.cleanup = cleanup;
        }

        @EventListener(ApplicationReadyEvent.class)
        void clean() {
            this.cleanup.run();
        }
    }
}
