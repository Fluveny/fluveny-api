package com.fluveny.fluveny_backend.auth;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O executor próprio da recuperação de senha (história 2.8): 1 thread e fila de {@value
 * #QUEUE_CAPACITY}. A busca da conta, o token e o outbox rodam aqui, depois da resposta, para o
 * tempo do pedido não depender de a conta existir. Fila cheia ou executor encerrado recusam a
 * tarefa ({@link #submit} diz qual). No encerramento da API, os pedidos na fila são descartados,
 * com a contagem no log; um pedido perdido custa só pedir de novo.
 *
 * <p>Não é um {@link Executor} do Spring de propósito: um bean desse tipo desligaria o executor
 * padrão do Boot.
 */
@Component
class PasswordRecoveryExecutor {

    static final int QUEUE_CAPACITY = 100;

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryExecutor.class);

    private final Executor delegate;

    PasswordRecoveryExecutor() {
        this(
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                        task -> {
                            Thread thread = new Thread(task, "password-recovery");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy()));
    }

    /** Para o teste: um executor síncrono ({@code Runnable::run}) ou um que recusa. */
    PasswordRecoveryExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Põe a tarefa na fila.
     *
     * @return {@link Submission#ACCEPTED}; senão, por que a tarefa foi descartada
     */
    Submission submit(Runnable task) {
        try {
            this.delegate.execute(task);
            return Submission.ACCEPTED;
        } catch (RejectedExecutionException ex) {
            return (this.delegate instanceof ExecutorService service && service.isShutdown())
                    ? Submission.SHUT_DOWN
                    : Submission.QUEUE_FULL;
        }
    }

    /** Encerra sem esperar a fila: os pedidos que ainda não rodaram são descartados. */
    @PreDestroy
    void shutdown() {
        if (this.delegate instanceof ExecutorService service) {
            int dropped = service.shutdownNow().size();
            if (dropped > 0) {
                log.warn(
                        "Executor da recuperação de senha encerrado: {} pedido(s) na fila"
                                + " descartado(s)",
                        dropped);
            }
        }
    }

    /** O resultado de {@link #submit}. */
    enum Submission {
        ACCEPTED,
        QUEUE_FULL,
        SHUT_DOWN
    }
}
