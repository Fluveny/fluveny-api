package com.fluveny.fluveny_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe o contexto inteiro contra o MongoDB 8 do {@link LocalMongoContainer} (NFR13). Não precisa de
 * {@code SPRING_DATA_MONGODB_URI}: a {@code @ServiceConnection} vence a propriedade. Por isso roda
 * fora do perfil {@code prod}, que exige a variável; o {@code MongoUriBindingIT} sobe o contexto no
 * {@code prod}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class ApplicationIT {

    @Test
    void contextLoads() {}
}
