package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fluveny.fluveny_backend.account.PasswordReset;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * O executor da recuperação de senha (história 2.8), sem Spring: 1 thread, fila de 100 e, cheia, o
 * pedido é descartado com um aviso no log, sem o e-mail; no encerramento, a fila é descartada com a
 * contagem no log, e o pedido depois disso tem um aviso próprio.
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryExecutorTest {

    @Test
    void aFullQueueRejectsTheTask() throws Exception {
        PasswordRecoveryExecutor executor = new PasswordRecoveryExecutor();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(PasswordRecoveryExecutor.QUEUE_CAPACITY + 1);
        try {
            assertThat(
                            executor.submit(() -> {
                                running.countDown();
                                await(release);
                                done.countDown();
                            }))
                    .isEqualTo(PasswordRecoveryExecutor.Submission.ACCEPTED);
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < PasswordRecoveryExecutor.QUEUE_CAPACITY; i++) {
                assertThat(executor.submit(done::countDown))
                        .isEqualTo(PasswordRecoveryExecutor.Submission.ACCEPTED);
            }
            assertThat(executor.submit(done::countDown))
                    .isEqualTo(PasswordRecoveryExecutor.Submission.QUEUE_FULL);
        } finally {
            release.countDown();
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    @Test
    void shuttingDownDropsTheQueueWithACountAndRefusesNewTasks(CapturedOutput output)
            throws Exception {
        PasswordRecoveryExecutor executor = new PasswordRecoveryExecutor();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        try {
            executor.submit(() -> {
                running.countDown();
                await(release);
            });
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            executor.submit(ran::incrementAndGet);
            executor.submit(ran::incrementAndGet);

            executor.shutdown();

            assertThat(output.getAll())
                    .contains("Executor da recuperação de senha encerrado: 2 pedido(s) na fila");
            assertThat(executor.submit(ran::incrementAndGet))
                    .isEqualTo(PasswordRecoveryExecutor.Submission.SHUT_DOWN);
        } finally {
            release.countDown();
        }
        assertThat(ran).hasValue(0);
    }

    @Test
    void aRequestAfterShutdownIsLoggedAsShutDownNotAsAFullQueue(CapturedOutput output) {
        PasswordRecoveryExecutor executor = new PasswordRecoveryExecutor();
        executor.shutdown();
        PasswordReset reset = mock(PasswordReset.class);
        new PasswordRecovery(
                        reset,
                        mock(Sessions.class),
                        executor,
                        Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneOffset.UTC))
                .request("ana.fim@example.com", "203.0.113.10");

        verifyNoInteractions(reset);
        assertThat(output.getAll())
                .contains("Executor da recuperação de senha encerrado: pedido descartado")
                .doesNotContain("Fila da recuperação de senha cheia")
                .doesNotContain("ana.fim@example.com");
    }

    @Test
    void aDroppedRequestIsLoggedWithoutTheEmail(CapturedOutput output) {
        PasswordReset reset = mock(PasswordReset.class);
        PasswordRecovery recovery =
                new PasswordRecovery(
                        reset,
                        mock(Sessions.class),
                        new PasswordRecoveryExecutor(task -> {
                            throw new RejectedExecutionException("cheia");
                        }),
                        Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneOffset.UTC));

        recovery.request("ana.cheia@example.com", "203.0.113.9");

        verifyNoInteractions(reset);
        assertThat(output.getAll())
                .contains("Fila da recuperação de senha cheia")
                .doesNotContain("ana.cheia@example.com")
                .doesNotContain("203.0.113.9");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
