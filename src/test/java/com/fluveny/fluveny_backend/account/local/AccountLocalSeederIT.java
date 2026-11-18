package com.fluveny.fluveny_backend.account.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.TestSeedPassword;
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
 * dois Alunos de teste, e rodar de novo não duplica nada. A senha é a {@code LOCAL_SEED_PASSWORD}
 * gerada pela {@link TestSeedPassword}.
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
    void seedingTwiceKeepsExactlyTheTwoStudents() {
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
                        AccountLocalSeeder.WITH_PRONOUNS, AccountLocalSeeder.WITHOUT_PRONOUNS);
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
        for (Document user : users) {
            assertThat(user.getString("status")).isEqualTo("ACTIVE");
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
}
