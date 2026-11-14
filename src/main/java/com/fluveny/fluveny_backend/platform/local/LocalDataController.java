package com.fluveny.fluveny_backend.platform.local;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas do perfil {@code local} para o {@code mise run seed} e o {@code mise run reset}. Ficam fora
 * de {@code /api/v1}, portanto fora do contrato OpenAPI, e não existem em nenhum outro perfil.
 */
@RestController
@Profile("local")
@PublicRoute
@RequestMapping("/local")
class LocalDataController {

    private final LocalDataLoader loader;

    LocalDataController(LocalDataLoader loader) {
        this.loader = loader;
    }

    @PostMapping("/seed")
    ApiResponse<LocalData> seed() {
        return ApiResponse.of(new LocalData(this.loader.load()), "Carregadores executados.");
    }

    @PostMapping("/reset")
    ApiResponse<LocalData> reset() {
        return ApiResponse.of(
                new LocalData(this.loader.reset()), "Banco apagado e carregadores executados.");
    }

    record LocalData(List<String> seeders) {}
}
