package com.fluveny.fluveny_backend.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Contexto inteiro no perfil {@code prod} padrão, com os controllers de {@link PlatformTestRoutes}
 * e MockMvc. A URI aponta para um Mongo que não existe: nenhum teste daqui fala com o banco, e o
 * cliente não conecta na partida.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(PlatformTestRoutes.class)
@TestPropertySource(
        properties = "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests")
@interface PlatformWebTest {}
