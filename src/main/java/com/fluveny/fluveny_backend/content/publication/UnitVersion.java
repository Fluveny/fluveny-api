package com.fluveny.fluveny_backend.content.publication;

import java.time.Instant;

/**
 * Versão de Unidade, imutável e nunca apagada (AD-4). O id é {@code sha256:<hex>} do pacote em JSON
 * canônico (RFC 8785), e o próprio JSON canônico fica guardado como string: o Mongo não reordena
 * chaves nem muda números, e o hash pode ser recalculado a qualquer momento.
 *
 * @param id {@code sha256:<hex>} de {@code canonicalJson}
 * @param unitKey a Unidade
 * @param schemaVersion o {@code schemaVersion} do {@code unit.json} (AD-11)
 * @param order o {@code order} da Unidade no curso
 * @param canonicalJson o pacote {@code {unit, stages, media}} em JSON canônico
 * @param createdAt quando a versão foi gravada pela primeira vez
 */
public record UnitVersion(
        String id,
        String unitKey,
        int schemaVersion,
        int order,
        String canonicalJson,
        Instant createdAt) {}
