package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.account.PreparedStudent;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.RegisteredStudent;
import com.mongodb.MongoException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O cadastro com a sessão (histórias 2.1 e 2.6, AD-10): abre a transação Mongo que cobre a conta em
 * {@code users}, a migração do Visitante (que {@code learning} faz ao tratar o {@code
 * StudentRegistered}) e a sessão em {@code sessions}. Qualquer falha desfaz tudo; o {@code
 * STUDENT_REGISTERED}, o {@code VISITOR_MIGRATED} e a confirmação de e-mail saem só depois do
 * commit.
 *
 * <p>Um erro com o rótulo {@code TransientTransactionError} (por exemplo, dois cadastros que
 * consomem os mesmos recibos ao mesmo tempo) refaz a transação inteira, até {@link #ATTEMPTS}
 * vezes, com uma espera curta e aleatória entre elas. Na nova tentativa, os recibos que o outro
 * cadastro consumiu são descartados, e o e-mail que o outro gravou dá {@code 409
 * EMAIL_ALREADY_REGISTERED}. A senha é conferida e o BCrypt roda uma vez, antes da transação.
 */
@Service
public class Signup {

    /** Quantas vezes a transação roda, no máximo. */
    static final int ATTEMPTS = 3;

    /** A espera antes da tentativa {@code n} fica entre {@code n} e {@code 3n} vezes isto. */
    static final long BACKOFF_MS = 25;

    private static final Logger log = LoggerFactory.getLogger(Signup.class);

    private final RegisterStudent register;

    private final Sessions sessions;

    private final TransactionTemplate transaction;

    Signup(
            RegisterStudent register,
            Sessions sessions,
            PlatformTransactionManager transactionManager) {
        this.register = register;
        this.sessions = sessions;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Cria a conta e a sessão, juntas ou nenhuma.
     *
     * @throws com.fluveny.fluveny_backend.platform.web.ApiException os erros do {@link
     *     RegisterStudent#register}; em todos, nada é gravado
     */
    public Registered signup(NewStudent student) {
        PreparedStudent prepared = this.register.prepare(student);
        for (int attempt = 1; ; attempt++) {
            try {
                return this.transaction.execute(status -> {
                    RegisteredStudent registered = this.register.register(prepared);
                    Sessions.NewSession session =
                            this.sessions.create(registered.userId(), registered.role());
                    return new Registered(registered, session);
                });
            } catch (RuntimeException ex) {
                if (attempt >= ATTEMPTS || !isTransient(ex)) {
                    throw ex;
                }
                log.warn("Cadastro refeito após erro transitório da transação ({})", attempt);
                backOff(attempt, ex);
            }
        }
    }

    /** Espera um pouco, com variação, para as transações concorrentes não colidirem de novo. */
    private static void backOff(int attempt, RuntimeException cause) {
        long millis = BACKOFF_MS * attempt * ThreadLocalRandom.current().nextLong(1, 4);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    /** Se a causa é um erro do Mongo com o rótulo {@code TransientTransactionError}. */
    static boolean isTransient(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = ex; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A conta e a sessão criadas.
     *
     * @param session o token, que nunca vai para o log, e o vencimento
     */
    public record Registered(RegisteredStudent student, Sessions.NewSession session) {}
}
