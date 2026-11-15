package com.fluveny.fluveny_backend.content.publication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Coleção {@code unit_versions} (dona: {@code content}). Só insere; nunca apaga nem reescreve. */
public interface UnitVersionStore {

    /**
     * Grava a versão se o id ainda não existe.
     *
     * @return {@code true} se gravou; {@code false} se o id já existia (nada muda)
     */
    boolean insertIfAbsent(UnitVersion version);

    Optional<UnitVersion> findById(String id);

    List<UnitVersion> findAllById(Collection<String> ids);
}
