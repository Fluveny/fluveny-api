package com.fluveny.fluveny_backend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.auth.MutableClock;
import com.fluveny.fluveny_backend.mail.infrastructure.MailStatus;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * O despacho do outbox (história 2.2), sem Mongo e sem SMTP: o {@link InMemoryOutbox}, um
 * transporte falso e um relógio que o teste controla. O relógio começa em 19/11/2026, 12h de
 * Brasília.
 */
@ExtendWith(OutputCaptureExtension.class)
class MailDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    private final InMemoryOutbox outbox = new InMemoryOutbox();

    private final MutableClock clock = new MutableClock(NOW);

    private final FakeTransport transport = new FakeTransport();

    private final OutboxMailSender sender = new OutboxMailSender(this.outbox, this.clock);

    private MailDispatcher dispatcher(int quota) {
        return new MailDispatcher(this.outbox, this.transport, this.clock, quota);
    }

    /** Transporte falso: guarda os destinatários; falha enquanto {@code failing} estiver ligado. */
    static final class FakeTransport implements MailTransport {

        final List<String> sent = Collections.synchronizedList(new ArrayList<>());

        volatile boolean configured = true;

        volatile boolean failing;

        volatile RuntimeException failure;

        @Override
        public boolean configured() {
            return this.configured;
        }

        @Override
        public void send(String to, String subject, String text) {
            if (this.failure != null) {
                throw this.failure;
            }
            if (this.failing) {
                throw new IllegalStateException("550 caixa inexistente: " + to);
            }
            this.sent.add(to);
        }
    }

    private OutboxMail only() {
        assertThat(this.outbox.all()).hasSize(1);
        return this.outbox.all().getFirst();
    }

    @Test
    void enqueueOnlyStoresAPendingMailAndSendsNothing() {
        this.sender.enqueue("ana@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        OutboxMail mail = only();
        assertThat(mail.status()).isEqualTo(MailStatus.PENDING);
        assertThat(mail.priority()).isEqualTo(MailPriority.NORMAL);
        assertThat(mail.attempts()).isZero();
        assertThat(mail.nextAttemptAt()).isEqualTo(NOW);
        // Sem SMTP, o TTL apaga o PENDING em 30 dias, com o endereço e o link.
        assertThat(mail.purgeAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(this.transport.sent).isEmpty();
    }

    @Test
    void enqueueRefusesBlankFields() {
        assertThatThrownBy(() -> this.sender.enqueue(" ", "a", "b", MailPriority.HIGH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.sender.enqueue("a@b.c", "a", "b", null))
                .isInstanceOf(NullPointerException.class);
        assertThat(this.outbox.all()).isEmpty();
    }

    @Test
    void aDuePendingMailIsSent() {
        this.sender.enqueue("ana@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        this.clock.advance(Duration.ofSeconds(30));

        assertThat(dispatcher(300).dispatch()).isEqualTo(1);

        OutboxMail mail = only();
        assertThat(this.transport.sent).containsExactly("ana@example.com");
        assertThat(mail.status()).isEqualTo(MailStatus.SENT);
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.sentAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(mail.purgeAt()).isEqualTo(NOW.plusSeconds(30).plus(Duration.ofDays(30)));

        // Rodar de novo não reenvia.
        assertThat(dispatcher(300).dispatch()).isZero();
        assertThat(this.transport.sent).hasSize(1);
    }

    @Test
    void withoutTransportNothingIsClaimedAndTheMailStaysPending() {
        this.transport.configured = false;
        this.sender.enqueue("ana@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        assertThat(dispatcher(300).dispatch()).isZero();
        OutboxMail mail = only();
        assertThat(mail.status()).isEqualTo(MailStatus.PENDING);
        assertThat(mail.attempts()).isZero();
        assertThat(mail.nextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void aFailureCountsTheAttemptAndBacksOffExponentiallyUntilFailedOnTheEighth(
            CapturedOutput output) {
        this.transport.failing = true;
        this.sender.enqueue("ana.falha@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        MailDispatcher dispatcher = dispatcher(300);
        long[] expectedMinutes = {1, 2, 4, 8, 16, 32, 60};
        for (int attempt = 1; attempt <= 7; attempt++) {
            Instant before = this.clock.instant();
            assertThat(dispatcher.dispatch()).isZero();
            OutboxMail mail = only();
            assertThat(mail.status()).isEqualTo(MailStatus.PENDING);
            assertThat(mail.attempts()).isEqualTo(attempt);
            assertThat(mail.nextAttemptAt())
                    .isEqualTo(before.plus(Duration.ofMinutes(expectedMinutes[attempt - 1])));

            // Antes do prazo, nada sai nem conta tentativa.
            this.clock.set(mail.nextAttemptAt().minusMillis(1));
            assertThat(dispatcher.dispatch()).isZero();
            assertThat(only().attempts()).isEqualTo(attempt);
            this.clock.set(mail.nextAttemptAt());
        }
        assertThat(dispatcher.dispatch()).isZero();
        OutboxMail failed = only();
        assertThat(failed.status()).isEqualTo(MailStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(8);
        assertThat(failed.purgeAt()).isEqualTo(this.clock.instant().plus(Duration.ofDays(30)));

        // FAILED não volta à fila.
        this.transport.failing = false;
        this.clock.advance(Duration.ofDays(1));
        assertThat(dispatcher.dispatch()).isZero();
        assertThat(this.transport.sent).isEmpty();

        // O log leva o id, nunca o endereço (que a mensagem do SMTP traz).
        assertThat(output.getAll()).contains(failed.id()).doesNotContain("ana.falha@example.com");
    }

    @Test
    void aRejectedSmtpLoginSpendsNoAttemptWaitsAnHourAndStopsTheRound(CapturedOutput output) {
        this.transport.failure =
                new org.springframework.mail.MailAuthenticationException(
                        "535 senha errada para ana.auth@example.com");
        this.sender.enqueue("ana.auth@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        this.clock.advance(Duration.ofSeconds(1));
        this.sender.enqueue("bia.auth@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        MailDispatcher dispatcher = dispatcher(300);

        for (int round = 0; round < 10; round++) {
            assertThat(dispatcher.dispatch()).isZero();
            this.clock.advance(Duration.ofHours(1));
        }
        List<OutboxMail> mails = this.outbox.all();
        assertThat(mails).extracting(OutboxMail::status).containsOnly(MailStatus.PENDING);
        assertThat(mails).extracting(OutboxMail::attempts).containsOnly(0);

        // Corrigida a credencial, tudo sai.
        this.transport.failure = null;
        assertThat(dispatcher.dispatch()).isEqualTo(2);
        assertThat(output.getAll())
                .contains("MailAuthenticationException")
                .doesNotContain("ana.auth@example.com")
                .doesNotContain("bia.auth@example.com");
    }

    @Test
    void aRejectedLoginStopsTheRoundBeforeTheNextMail() {
        this.transport.failure = new org.springframework.mail.MailAuthenticationException("535");
        this.sender.enqueue("a@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        this.clock.advance(Duration.ofSeconds(1));
        this.sender.enqueue("b@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        Instant now = this.clock.instant();

        assertThat(dispatcher(300).dispatch()).isZero();

        assertThat(this.outbox.all().getFirst().nextAttemptAt())
                .isEqualTo(now.plus(Duration.ofHours(1)));
        // O segundo nem foi marcado.
        assertThat(this.outbox.all().getLast().nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void theBackoffGoesFromOneMinuteToOneHour() {
        assertThat(MailDispatcher.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(MailDispatcher.backoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(MailDispatcher.backoff(6)).isEqualTo(Duration.ofMinutes(32));
        assertThat(MailDispatcher.backoff(7)).isEqualTo(Duration.ofHours(1));
        assertThat(MailDispatcher.backoff(40)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void anExhaustedQuotaWaitsForTheTurnOfTheDayInSaoPauloWithoutSpendingAttempts() {
        // 23h de Brasília: ainda é 19/11 lá, 20/11 em UTC.
        this.clock.set(Instant.parse("2026-11-20T02:00:00Z"));
        MailDispatcher dispatcher = dispatcher(2);
        for (int i = 0; i < 3; i++) {
            this.sender.enqueue("a" + i + "@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        }
        assertThat(dispatcher.dispatch()).isEqualTo(2);
        assertThat(dispatcher.dispatch()).isZero();
        OutboxMail waiting = this.outbox.all().getLast();
        assertThat(waiting.status()).isEqualTo(MailStatus.PENDING);
        assertThat(waiting.attempts()).isZero();

        // 23h59 em Brasília: mesma cota.
        this.clock.set(Instant.parse("2026-11-20T02:59:59Z"));
        assertThat(dispatcher.dispatch()).isZero();

        // Meia-noite em Brasília: cota nova.
        this.clock.set(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(dispatcher.dispatch()).isEqualTo(1);
        assertThat(this.transport.sent).hasSize(3);
    }

    @Test
    void withOneOfQuotaTheHighPriorityMailGoesFirst() {
        this.sender.enqueue("normal@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        this.clock.advance(Duration.ofSeconds(1));
        this.sender.enqueue("high@example.com", "Assunto", "Texto", MailPriority.HIGH);
        assertThat(dispatcher(1).dispatch()).isEqualTo(1);
        assertThat(this.transport.sent).containsExactly("high@example.com");
    }

    @Test
    void theSamePriorityGoesOutOldestFirst() {
        this.sender.enqueue("first@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        this.clock.advance(Duration.ofSeconds(1));
        this.sender.enqueue("second@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        assertThat(dispatcher(300).dispatch()).isEqualTo(2);
        assertThat(this.transport.sent).containsExactly("first@example.com", "second@example.com");
    }

    @Test
    void twoSimultaneousDispatchesSendEachMailOnce() throws Exception {
        for (int i = 0; i < 40; i++) {
            this.sender.enqueue("a" + i + "@example.com", "Assunto", "Texto", MailPriority.NORMAL);
        }
        // Dois despachantes, como duas instâncias: cada um com o próprio objeto.
        List<MailDispatcher> dispatchers = List.of(dispatcher(300), dispatcher(300));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        int total = 0;
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (MailDispatcher dispatcher : dispatchers) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return dispatcher.dispatch();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                total += result.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(total).isEqualTo(40);
        assertThat(this.transport.sent).hasSize(40).doesNotHaveDuplicates();
    }

    @Test
    void aNegativeQuotaIsRefused() {
        assertThatThrownBy(() -> dispatcher(-1)).isInstanceOf(IllegalStateException.class);
    }
}
