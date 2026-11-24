package com.fluveny.fluveny_backend.platform.events;

import com.mongodb.MongoException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A retentativa das transações Mongo (histórias 2.6 e 2.10): um erro com o rótulo {@code
 * TransientTransactionError} refaz a transação inteira, até {@link #ATTEMPTS} vezes, com uma espera
 * curta e aleatória entre elas. Qualquer outro erro sobe na hora.
 */
public final class TransientTransactions {

    /** Quantas vezes a transação roda, no máximo. */
    public static final int ATTEMPTS = 3;

    /** A espera antes da tentativa {@code n} fica entre {@code n} e {@code 3n} vezes isto. */
    static final long BACKOFF_MS = 25;

    private static final Logger log = LoggerFactory.getLogger(TransientTransactions.class);

    private TransientTransactions() {}

    /**
     * Roda {@code transaction}, refazendo-a nos erros transitórios.
     *
     * @param what o nome da operação, para o log (sem dado pessoal)
     */
    public static <T> T run(String what, Supplier<T> transaction) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.get();
            } catch (RuntimeException ex) {
                if (attempt >= ATTEMPTS || !isTransient(ex)) {
                    throw ex;
                }
                log.warn("{} refeito após erro transitório da transação ({})", what, attempt);
                backOff(attempt, ex);
            }
        }
    }

    /** Se a causa é um erro do Mongo com o rótulo {@code TransientTransactionError}. */
    public static boolean isTransient(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = ex; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
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
}
