package com.fluveny.fluveny_backend.platform;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Sem o segredo de proxy configurado (o perfil {@code local}, os testes fora de {@code prod}), o
 * filtro de borda fica desligado (história 1.6): uma requisição sem {@code X-Fluveny-Proxy} chega à
 * rota normalmente. A propriedade vazia vence a variável que a {@code TestProxySecret} põe na JVM.
 */
@PlatformWebTest
@TestPropertySource(properties = "fluveny.proxy.secret=")
class ProxySecretOffTest {

    @Autowired private WebApplicationContext context;

    @Test
    void withoutTheSecretConfiguredTheFilterIsOff() throws Exception {
        MockMvc mvc =
                MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
        String id = "65f1a2b3c4d5e6f7a8b9c0d1";
        mvc.perform(get("/test/open/x/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(id));
    }
}
