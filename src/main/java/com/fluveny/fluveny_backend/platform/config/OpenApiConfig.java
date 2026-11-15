package com.fluveny.fluveny_backend.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cabeçalho fixo do contrato (AD-11). Com {@code servers} declarado, o springdoc não calcula o
 * servidor pela requisição, e o {@code /v3/api-docs} sai igual em qualquer máquina e ambiente.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static final String SERVER_URL = "/";

    @Bean
    OpenAPI fluvenyOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Fluveny API").version("v1"))
                .servers(List.of(new Server().url(SERVER_URL).description("Mesma origem da API")));
    }
}
