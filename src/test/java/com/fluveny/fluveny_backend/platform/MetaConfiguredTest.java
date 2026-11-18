package com.fluveny.fluveny_backend.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/meta} com a versão vinda de {@code FLUVENY_MIN_APP_VERSION} e dois {@link
 * ActivityTypeSource} de teste, na ordem, antes do source real do {@code learning}. Contexto
 * próprio, para os outros testes continuarem vendo a lista vazia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MetaConfiguredTest.Sources.class)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "FLUVENY_MIN_APP_VERSION=1.4.0"
        })
class MetaConfiguredTest {

    @Autowired private MockMvc mvc;

    @Test
    void metaShowsTheConfiguredVersionAndTheSourcesInOrder() throws Exception {
        this.mvc
                .perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minAppVersion").value("1.4.0"))
                .andExpect(jsonPath("$.data.activityTypes.length()").value(12))
                .andExpect(jsonPath("$.data.activityTypes[0].type").value("multiple-choice"))
                .andExpect(jsonPath("$.data.activityTypes[0].sinceAppVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.activityTypes[1].type").value("fill-in"))
                .andExpect(jsonPath("$.data.activityTypes[2].type").value("speech"))
                .andExpect(jsonPath("$.data.activityTypes[2].sinceAppVersion").value("1.4.0"))
                // O source real do learning, sem @Order, vem por último. Todos os Tipos dele
                // têm a tag v0.1.0 do App (história 1.20).
                .andExpect(jsonPath("$.data.activityTypes[3].type").value("choice"))
                .andExpect(jsonPath("$.data.activityTypes[3].sinceAppVersion").value("0.1.0"))
                // História 1.13: o Diálogo.
                .andExpect(jsonPath("$.data.activityTypes[4].type").value("dialogue"))
                .andExpect(jsonPath("$.data.activityTypes[4].sinceAppVersion").value("0.1.0"))
                // História 1.14: os Cartões de expressões e a Expressão Útil destacada.
                .andExpect(jsonPath("$.data.activityTypes[5].type").value("cards"))
                .andExpect(jsonPath("$.data.activityTypes[5].sinceAppVersion").value("0.1.0"))
                .andExpect(jsonPath("$.data.activityTypes[6].type").value("expression"))
                .andExpect(jsonPath("$.data.activityTypes[6].sinceAppVersion").value("0.1.0"))
                // História 1.15: a Fala.
                .andExpect(jsonPath("$.data.activityTypes[7].type").value("speech"))
                .andExpect(jsonPath("$.data.activityTypes[7].sinceAppVersion").value("0.1.0"))
                // História 1.16: o Associar.
                .andExpect(jsonPath("$.data.activityTypes[8].type").value("match"))
                .andExpect(jsonPath("$.data.activityTypes[8].sinceAppVersion").value("0.1.0"))
                // História 1.17: as Lacunas.
                .andExpect(jsonPath("$.data.activityTypes[9].type").value("gaps"))
                .andExpect(jsonPath("$.data.activityTypes[9].sinceAppVersion").value("0.1.0"))
                // História 1.18: a Resposta Livre.
                .andExpect(jsonPath("$.data.activityTypes[10].type").value("free"))
                .andExpect(jsonPath("$.data.activityTypes[10].sinceAppVersion").value("0.1.0"))
                // História 1.19: o Grammar Time.
                .andExpect(jsonPath("$.data.activityTypes[11].type").value("grammar"))
                .andExpect(jsonPath("$.data.activityTypes[11].sinceAppVersion").value("0.1.0"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Sources {

        // Registrados fora da ordem: a ordem vem do @Order.
        @Bean
        @Order(2)
        ActivityTypeSource speechSource() {
            return () -> List.of(new ActivityTypeSupport("speech", "1.4.0"));
        }

        @Bean
        @Order(1)
        ActivityTypeSource basicSource() {
            return () ->
                    List.of(
                            new ActivityTypeSupport("multiple-choice", "1.0.0"),
                            new ActivityTypeSupport("fill-in", "1.0.0"));
        }
    }
}
