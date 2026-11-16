package com.fluveny.fluveny_backend.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O relógio da API, em UTC. Por enquanto só os limites de taxa do Visitante o usam (história 1.12);
 * os testes trocam por um relógio fixo com um bean {@code @Primary}.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
