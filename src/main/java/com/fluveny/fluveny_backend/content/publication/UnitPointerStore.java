package com.fluveny.fluveny_backend.content.publication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Coleção {@code unit_pointers} (dona: {@code content}): o ponteiro {@code published}. */
public interface UnitPointerStore {

    /** Aponta a Unidade para a versão, criando o ponteiro se ainda não existe. */
    void move(String unitKey, String versionId, Instant now);

    Optional<UnitPointer> findById(String unitKey);

    List<UnitPointer> findAll();
}
