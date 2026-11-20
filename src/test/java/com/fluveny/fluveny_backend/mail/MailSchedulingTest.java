package com.fluveny.fluveny_backend.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.TestPropertySource;

/**
 * O despacho agendado do outbox (história 2.2) liga por padrão e desliga com {@code
 * fluveny.mail.dispatch.enabled=false}, sem desligar o agendamento da API, que fica na {@code
 * platform}. Sem Mongo; sem SMTP, a rodada não toca no banco.
 */
class MailSchedulingTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(
            properties = {
                "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
                "fluveny.mongo.indexes-on-startup=false"
            })
    class ByDefault {

        @Autowired private ApplicationContext context;

        @Test
        void theDispatchIsScheduled() {
            assertThat(this.context.getBeanNamesForType(MailScheduling.class)).hasSize(1);
            assertThat(this.context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                    .isNotEmpty();
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(
            properties = {
                "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
                "fluveny.mongo.indexes-on-startup=false",
                "fluveny.mail.dispatch.enabled=false"
            })
    class TurnedOff {

        @Autowired private ApplicationContext context;

        @Test
        void theDispatchIsNotScheduledButSchedulingStaysOn() {
            assertThat(this.context.getBeanNamesForType(MailScheduling.class)).isEmpty();
            assertThat(this.context.getBeanNamesForType(MailDispatcher.class)).hasSize(1);
            assertThat(this.context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                    .isNotEmpty();
        }
    }
}
