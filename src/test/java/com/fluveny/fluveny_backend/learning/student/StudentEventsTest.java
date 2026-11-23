package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

/** O {@code ACTIVITY_STARTED} do Aluno (história 2.4), sem Mongo. */
class StudentEventsTest extends StudentLearningTestSupport {

    @Test
    void anActivityStartIsStoredWithTheMetricsIdAndNoDevice() throws Exception {
        send(EVENTS, started("s2-flags"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        assertThat(this.accounts.events)
                .containsExactly(
                        new Event(
                                EventType.ACTIVITY_STARTED,
                                NOW,
                                null,
                                "unit-1",
                                this.versionId,
                                "s2-flags",
                                null,
                                this.metricsId.toString(),
                                null));
        assertThat(this.accounts.events.getFirst().toString())
                .doesNotContain(this.userId, "Ana Secreta", "@example.com");
    }

    @Test
    void anUnknownActivityIs404AndNothingIsStored() throws Exception {
        expectProblem(send(EVENTS, started("s1-nao-existe")), 404, "NOT_FOUND");
        assertThat(this.accounts.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deviceId", "userId", "unitKey"})
    void anUnknownFieldIs400(String field) throws Exception {
        ObjectNode body = started("s1-greetings");
        body.put(field, "x");
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
        assertThat(this.accounts.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VISITOR_SESSION_STARTED", "ACTIVITY_COMPLETED"})
    void anotherTypeIs400(String type) throws Exception {
        ObjectNode body = started("s1-greetings");
        body.put("type", type);
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
        assertThat(this.accounts.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"versionId", "activityKey"})
    void aMissingFieldIs400(String field) throws Exception {
        ObjectNode body = started("s1-greetings");
        body.remove(field);
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
    }

    @Test
    void withoutSessionIs401() throws Exception {
        expectProblem(send(EVENTS, started("s1-greetings"), null), 401, "UNAUTHENTICATED");
        assertThat(this.accounts.events).isEmpty();
    }
}
