package com.fluveny.fluveny_backend.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.RegisteredStudent;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

/**
 * {@code users} contra o Mongo 8 (história 2.1): os índices únicos do e-mail e do {@code
 * metricsId}, os campos gravados (inclusive os da AD-13, nulos), o e-mail repetido em outra caixa e
 * a corrida de cadastros do mesmo e-mail.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class AccountMongoIT {

    @Autowired private MongoTemplate mongo;

    @Autowired private AccountIndexes indexes;

    @Autowired private RegisterStudent register;

    @Autowired private UserStore users;

    /** Apaga só os documentos: os índices ficam os da partida ({@code OnStartup}). */
    @BeforeEach
    void cleanCollection() {
        this.mongo.remove(new Query(), MongoUserStore.COLLECTION);
        this.mongo.remove(new Query(), "events");
    }

    private static NewStudent student(String email) {
        return new NewStudent(email, "senha123", LocalDate.of(1995, 5, 20), "Ana", "0.1", "0.2");
    }

    private List<Document> documents() {
        List<Document> documents = new ArrayList<>();
        this.mongo.getCollection(MongoUserStore.COLLECTION).find().into(documents);
        return documents;
    }

    @Test
    void theEmailAndMetricsIdIndexesAreUniqueAndEnsureIsIdempotent() {
        this.indexes.ensure();
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoUserStore.COLLECTION).listIndexes().into(indexes);
        for (String[] expected :
                new String[][] {
                    {AccountIndexes.EMAIL_INDEX, "email"},
                    {AccountIndexes.METRICS_ID_INDEX, "metricsId"}
                }) {
            Document index =
                    indexes.stream()
                            .filter(i -> expected[0].equals(i.getString("name")))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError("Índice ausente: " + expected[0]));
            assertThat(index.getBoolean("unique")).isTrue();
            assertThat(index.get("key", Document.class).keySet()).containsExactly(expected[1]);
        }
    }

    @Test
    void aRegistrationStoresTheAccountFieldsAndTheNullLifecycleFields() {
        RegisteredStudent registered = this.register.register(student(" Ana@Example.com "));

        List<Document> documents = documents();
        assertThat(documents).hasSize(1);
        Document user = documents.getFirst();
        assertThat(user.keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "email",
                        "passwordHash",
                        "birthDate",
                        "role",
                        "status",
                        "metricsId",
                        "name",
                        "pronounSet",
                        "termsVersion",
                        "privacyVersion",
                        "termsAcceptedAt",
                        "createdAt",
                        "emailVerifiedAt",
                        "pendingEmail",
                        "deletionRequestedAt");
        assertThat(user.getObjectId("_id").toHexString()).isEqualTo(registered.userId());
        assertThat(user.getString("email")).isEqualTo("ana@example.com");
        assertThat(user.getString("passwordHash")).startsWith("$2").doesNotContain("senha123");
        assertThat(user.getString("birthDate")).isEqualTo("1995-05-20");
        assertThat(user.getString("role")).isEqualTo("STUDENT");
        assertThat(user.getString("status")).isEqualTo("ACTIVE");
        assertThat(user.getString("name")).isEqualTo("Ana");
        assertThat(user.get("pronounSet")).isNull();
        assertThat(user.getString("termsVersion")).isEqualTo("0.1");
        assertThat(user.getString("privacyVersion")).isEqualTo("0.2");
        assertThat(user.getDate("termsAcceptedAt")).isNotNull();
        assertThat(user.get("emailVerifiedAt")).isNull();
        assertThat(user.get("pendingEmail")).isNull();
        assertThat(user.get("deletionRequestedAt")).isNull();

        // O evento leva só o metricsId, que não é o id da conta.
        Document event = this.mongo.getCollection("events").find().first();
        assertThat(event).isNotNull();
        assertThat(event.keySet())
                .containsExactlyInAnyOrder("_id", "type", "occurredAt", "metricsId", "fromVisitor");
        assertThat(event.getString("type")).isEqualTo("STUDENT_REGISTERED");
        assertThat(event.getString("metricsId"))
                .isEqualTo(user.getString("metricsId"))
                .isNotEqualTo(registered.userId());
        assertThat(event.getBoolean("fromVisitor")).isFalse();
    }

    @Test
    void theStoreReadsBackAndUpdatesThePronouns() {
        String id = this.register.register(student("ana@example.com")).userId();
        assertThat(this.users.findById(id))
                .hasValueSatisfying(user -> {
                    assertThat(user.email()).isEqualTo("ana@example.com");
                    assertThat(user.birthDate()).isEqualTo(LocalDate.of(1995, 5, 20));
                    assertThat(user.pronounSet()).isNull();
                });
        assertThat(this.users.updatePronounSet(id, PronounSet.SHE)).isTrue();
        assertThat(this.users.findById(id))
                .get()
                .extracting(User::pronounSet)
                .isEqualTo(PronounSet.SHE);
        assertThat(this.users.updatePronounSet(id, null)).isTrue();
        assertThat(this.users.findById(id)).get().extracting(User::pronounSet).isNull();
        assertThat(this.users.findById("nao-e-id")).isEmpty();
        assertThat(this.users.updatePronounSet("0".repeat(24), PronounSet.HE)).isFalse();
    }

    @Test
    void aRepeatedEmailInAnotherCaseIsAConflictWithoutTheEmailInTheError() {
        this.register.register(student("ana@example.com"));
        assertThatThrownBy(() -> this.register.register(student("ANA@example.COM")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> {
                            assertThat(ex.getStatus().value()).isEqualTo(409);
                            assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                            assertThat(ex.getCause()).isNull();
                            assertThat(ex.getDetail()).doesNotContain("ana@example.com");
                        });
        assertThat(documents()).hasSize(1);
        assertThat(this.mongo.getCollection("events").countDocuments()).isEqualTo(1);
    }

    @Test
    void simultaneousRegistrationsOfTheSameEmailStoreExactlyOne() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        int created = 0;
        int conflicts = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            try {
                                this.register.register(student("ana@example.com"));
                                return true;
                            } catch (ApiException ex) {
                                return false;
                            }
                        }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    created++;
                } else {
                    conflicts++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
        assertThat(documents()).hasSize(1);
    }
}
