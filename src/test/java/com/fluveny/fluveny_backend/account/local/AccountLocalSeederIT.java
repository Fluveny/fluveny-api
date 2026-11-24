package com.fluveny.fluveny_backend.account.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.TestSeedPassword;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 1º critério de aceite da 2.1: no perfil {@code local}, o carregador de {@code account} cria os
 * Alunos de teste (o desativado vem da 2.3; o atrasado, da 2.8), e rodar de novo não duplica nada.
 * A senha é a {@code LOCAL_SEED_PASSWORD} gerada pela {@link TestSeedPassword}.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(LocalMongoContainer.class)
class AccountLocalSeederIT {

    @Autowired private MongoTemplate mongo;

    @Autowired private AccountLocalSeeder seeder;

    private List<Document> users() {
        List<Document> users = new ArrayList<>();
        this.mongo.getCollection("users").find().into(users);
        return users;
    }

    @Test
    void seedingTwiceKeepsExactlyTheFiveStudents() {
        // Já rodou na partida; roda mais duas vezes.
        this.seeder.seed();
        Map<String, Object> metricsIds = new HashMap<>();
        Map<String, Object> createdAts = new HashMap<>();
        for (Document user : users()) {
            metricsIds.put(user.getString("email"), user.get("metricsId"));
            createdAts.put(user.getString("email"), user.get("createdAt"));
        }
        this.seeder.seed();
        this.seeder.seed();

        List<Document> users = users();
        // O seed de novo não troca o metricsId nem o createdAt.
        for (Document user : users) {
            assertThat(user.get("metricsId")).isEqualTo(metricsIds.get(user.getString("email")));
            assertThat(user.get("createdAt")).isEqualTo(createdAts.get(user.getString("email")));
        }
        assertThat(users)
                .extracting(user -> user.getString("email"))
                .containsExactlyInAnyOrder(
                        AccountLocalSeeder.WITH_PRONOUNS,
                        AccountLocalSeeder.WITHOUT_PRONOUNS,
                        AccountLocalSeeder.DEACTIVATED,
                        AccountLocalSeeder.OVERDUE,
                        AccountLocalSeeder.GUARDIAN_PENDING);
        Document teen =
                users.stream()
                        .filter(u ->
                                AccountLocalSeeder.GUARDIAN_PENDING.equals(u.getString("email")))
                        .findFirst()
                        .orElseThrow();
        assertThat(teen.getString("status")).isEqualTo("PENDING_GUARDIAN_CONSENT");
        assertThat(teen.getString("guardianEmail")).isEqualTo(AccountLocalSeeder.GUARDIAN);
        Document withPronouns =
                users.stream()
                        .filter(u -> AccountLocalSeeder.WITH_PRONOUNS.equals(u.getString("email")))
                        .findFirst()
                        .orElseThrow();
        Document withoutPronouns =
                users.stream()
                        .filter(u ->
                                AccountLocalSeeder.WITHOUT_PRONOUNS.equals(u.getString("email")))
                        .findFirst()
                        .orElseThrow();
        assertThat(withPronouns.getString("pronounSet")).isEqualTo("SHE");
        assertThat(withPronouns.getString("name")).isNotBlank();
        assertThat(withoutPronouns.get("pronounSet")).isNull();
        // História 2.2: um confirmado, outro por confirmar.
        assertThat(withPronouns.getDate("emailVerifiedAt")).isNotNull();
        assertThat(withoutPronouns.get("emailVerifiedAt")).isNull();
        // História 2.8: ativo, sem confirmar e criado há 8 dias, para a faixa.
        Document overdue =
                users.stream()
                        .filter(u -> AccountLocalSeeder.OVERDUE.equals(u.getString("email")))
                        .findFirst()
                        .orElseThrow();
        assertThat(overdue.get("emailVerifiedAt")).isNull();
        assertThat(overdue.getDate("createdAt").toInstant())
                .isBefore(Instant.now().minus(Duration.ofDays(7)));
        for (Document user : users) {
            // História 2.3: um desativado, para o 403 ACCOUNT_DEACTIVATED do login.
            assertThat(user.getString("status"))
                    .isEqualTo(
                            switch (user.getString("email")) {
                                case AccountLocalSeeder.DEACTIVATED -> "DEACTIVATED";
                                case AccountLocalSeeder.GUARDIAN_PENDING ->
                                        "PENDING_GUARDIAN_CONSENT";
                                default -> "ACTIVE";
                            });
            assertThat(user.getString("role")).isEqualTo("STUDENT");
            assertThat(user.getString("metricsId")).isNotBlank();
            assertThat(
                            new BCryptPasswordEncoder()
                                    .matches(
                                            TestSeedPassword.value(),
                                            user.getString("passwordHash")))
                    .isTrue();
        }
        // O seed não publica eventos: STUDENT_REGISTERED é só do cadastro.
        assertThat(this.mongo.getCollection("events").countDocuments()).isZero();
    }

    /** História 2.9: o seed de novo desfaz a troca de e-mail pendente e volta o país. */
    @Test
    void reseedingUndoesThePendingEmailChangeAndRestoresTheCountry() {
        this.seeder.seed();
        Document user =
                this.mongo
                        .getCollection("users")
                        .find(new Document("email", AccountLocalSeeder.WITH_PRONOUNS))
                        .first();
        this.mongo
                .getCollection("users")
                .updateOne(
                        new Document("_id", user.get("_id")),
                        new Document(
                                "$set",
                                new Document("pendingEmail", "nova@example.com")
                                        .append("country", "Chile")));
        this.mongo
                .getCollection("one_time_tokens")
                .insertOne(
                        new Document("tokenHash", "seed-it-hash")
                                .append("userId", user.get("_id"))
                                .append("purpose", "EMAIL_CHANGE")
                                .append("email", "nova@example.com")
                                .append("createdAt", new java.util.Date())
                                .append(
                                        "expiresAt",
                                        java.util.Date.from(
                                                Instant.now().plus(Duration.ofDays(1)))));

        this.seeder.seed();

        Document after =
                this.mongo
                        .getCollection("users")
                        .find(new Document("email", AccountLocalSeeder.WITH_PRONOUNS))
                        .first();
        assertThat(after.get("pendingEmail")).isNull();
        assertThat(after.getString("country")).isEqualTo(AccountLocalSeeder.COUNTRY);
        assertThat(
                        this.mongo
                                .getCollection("one_time_tokens")
                                .countDocuments(new Document("purpose", "EMAIL_CHANGE")))
                .isZero();
    }
}
