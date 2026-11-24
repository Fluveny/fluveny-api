package com.fluveny.fluveny_backend.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.mail.MailPriority;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code mail_outbox} contra o Mongo 8 (história 2.2): os índices (o TTL do {@code purgeAt}), os
 * campos gravados, a ordem do despacho (prioridade e {@code createdAt}), a marca atômica do {@code
 * claimNext} sob concorrência e a conta da cota.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
@TestPropertySource(properties = "fluveny.mail.dispatch.enabled=false")
class MailOutboxMongoIT {

    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    @Autowired private MongoTemplate mongo;

    @Autowired private MailIndexes indexes;

    @Autowired private OutboxStore store;

    @BeforeEach
    void clean() {
        this.mongo.remove(new Query(), MongoOutboxStore.COLLECTION);
    }

    private String enqueue(String to, MailPriority priority, Instant createdAt) {
        return this.store.insert(
                new OutboxMail(
                        null,
                        to,
                        "Assunto",
                        "Texto",
                        priority,
                        MailStatus.PENDING,
                        0,
                        createdAt,
                        createdAt,
                        null,
                        null,
                        false));
    }

    @Test
    void theIndexesExistAndEnsureIsIdempotent() {
        this.indexes.ensure();
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoOutboxStore.COLLECTION).listIndexes().into(indexes);
        Document ttl =
                indexes.stream()
                        .filter(i -> MailIndexes.TTL_INDEX.equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice TTL ausente"));
        assertThat(ttl.get("key", Document.class).keySet()).containsExactly("purgeAt");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isZero();
        assertThat(indexes)
                .extracting(i -> i.getString("name"))
                .contains(MailIndexes.DISPATCH_INDEX, MailIndexes.SENT_INDEX);
    }

    @Test
    void aPendingMailIsStoredWithTheOutboxFields() {
        enqueue("ana@example.com", MailPriority.NORMAL, NOW);
        Document document = this.mongo.getCollection(MongoOutboxStore.COLLECTION).find().first();
        assertThat(document).isNotNull();
        assertThat(document.keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "to",
                        "subject",
                        "text",
                        "priority",
                        "status",
                        "attempts",
                        "nextAttemptAt",
                        "createdAt",
                        "sentAt",
                        "purgeAt",
                        "purgeOnSend");
        assertThat(document.getString("status")).isEqualTo("PENDING");
        assertThat(document.getString("priority")).isEqualTo("NORMAL");
        assertThat(document.get("purgeAt")).isNull();
    }

    @Test
    void claimTakesHighFirstThenTheOldestAndOnlyDueMails() {
        enqueue("normal-old@example.com", MailPriority.NORMAL, NOW.minusSeconds(60));
        enqueue("high@example.com", MailPriority.HIGH, NOW);
        enqueue("future@example.com", MailPriority.HIGH, NOW.plusSeconds(60));
        enqueue("normal-new@example.com", MailPriority.NORMAL, NOW.minusSeconds(30));

        Instant lease = NOW.plus(Duration.ofMinutes(10));
        List<String> order = new ArrayList<>();
        Optional<OutboxMail> claimed;
        while ((claimed = this.store.claimNext(NOW, lease)).isPresent()) {
            order.add(claimed.get().to());
        }
        assertThat(order)
                .containsExactly(
                        "high@example.com", "normal-old@example.com", "normal-new@example.com");
        // A marca adiou o nextAttemptAt: depois do prazo, voltam à fila.
        assertThat(this.store.claimNext(lease, lease.plusSeconds(1))).isPresent();
    }

    @Test
    void simultaneousClaimsTakeEachMailOnce() throws Exception {
        for (int i = 0; i < 30; i++) {
            enqueue("a" + i + "@example.com", MailPriority.NORMAL, NOW.plusMillis(i));
        }
        Instant now = NOW.plusSeconds(1);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> claimed = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            Optional<OutboxMail> mail;
                            while ((mail = this.store.claimNext(now, now.plusSeconds(600)))
                                    .isPresent()) {
                                claimed.add(mail.get().id());
                            }
                            return null;
                        }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(claimed).hasSize(30).doesNotHaveDuplicates();
    }

    @Test
    void sentRetryAndFailedAreStoredAndTheQuotaCountsOnlySentSince() {
        String sent = enqueue("a@example.com", MailPriority.NORMAL, NOW);
        String retry = enqueue("b@example.com", MailPriority.NORMAL, NOW);
        String failed = enqueue("c@example.com", MailPriority.NORMAL, NOW);
        String old = enqueue("d@example.com", MailPriority.NORMAL, NOW);

        this.store.markSent(sent, 1, NOW, NOW.plus(Duration.ofDays(30)));
        this.store.markSent(old, 1, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(29)));
        this.store.markRetry(retry, 3, NOW.plus(Duration.ofMinutes(4)));
        this.store.markFailed(failed, 8, NOW.plus(Duration.ofDays(30)));

        assertThat(this.store.countSentSince(NOW.minus(Duration.ofHours(1)))).isEqualTo(1);
        assertThat(this.store.latest(10))
                .extracting(OutboxMail::status)
                .containsExactlyInAnyOrder(
                        MailStatus.SENT, MailStatus.SENT, MailStatus.PENDING, MailStatus.FAILED);
        OutboxMail retried =
                this.store.latest(10).stream()
                        .filter(m -> m.id().equals(retry))
                        .findFirst()
                        .orElseThrow();
        assertThat(retried.attempts()).isEqualTo(3);
        assertThat(retried.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(retried.purgeAt()).isNull();
    }
}
