package com.fluveny.fluveny_backend.platform.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fora do perfil {@code local} (aqui, o {@code prod} padrão), as rotas {@code /local/*} e os beans
 * do ambiente local não existem (AD-18): nada apaga nem carrega dados em homologação ou produção, e
 * o {@code GET /local/mail} não expõe o outbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class LocalProfileAbsentTest {

    @Autowired private ApplicationContext context;

    @Autowired private MockMvc mvc;

    @Test
    void localBeansDoNotExist() {
        assertThat(this.context.getBeanNamesForType(LocalDataLoader.class)).isEmpty();
        assertThat(this.context.getBeanNamesForType(LocalDataController.class)).isEmpty();
        assertThat(this.context.getBeanNamesForType(LocalProfileGuard.class)).isEmpty();
        // O GET /local/mail (história 2.2) mostra endereços e tokens.
        assertThat(this.context.containsBean("mailLocalController")).isFalse();
    }

    @Test
    void theLocalMailIsNotServed() throws Exception {
        int status = this.mvc.perform(get("/local/mail")).andReturn().getResponse().getStatus();

        assertThat(status).isGreaterThanOrEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/local/seed", "/local/reset"})
    void localRoutesAreNotServed(String path) throws Exception {
        int status = this.mvc.perform(post(path)).andReturn().getResponse().getStatus();

        assertThat(status).as(path).isGreaterThanOrEqualTo(400);
    }
}
