package com.fluveny.fluveny_backend;

import org.springframework.boot.SpringApplication;

/**
 * A API no perfil {@code local}, com o Mongo do {@link LocalMongoContainer}: é o que o {@code mise
 * run up} roda ({@code ./mvnw spring-boot:test-run}). Fica nos testes porque o Testcontainers não
 * entra na imagem. O Mongo é efêmero: a cada partida o banco nasce vazio e o {@code
 * LocalDataLoader} recria os dados.
 */
public class LocalFluvenyApplication {

    public static void main(String[] args) {
        SpringApplication.from(FluvenyBackendApplication::main)
                .with(LocalMongoContainer.class)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
