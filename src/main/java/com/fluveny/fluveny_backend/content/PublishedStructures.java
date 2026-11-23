package com.fluveny.fluveny_backend.content;

import com.fluveny.fluveny_backend.content.publication.PublishedUnits;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * A estrutura ordenada das Unidades publicadas para {@code learning} (história 2.5, AD-3): versão,
 * ordem, título e Estágios com as {@code activityKey}s na ordem do conteúdo. Lê a versão publicada
 * a cada chamada; o conteúdo já projetado fica no cache do {@link PublishedUnits}.
 */
@Service
public class PublishedStructures {

    private final PublishedUnits published;

    PublishedStructures(PublishedUnits published) {
        this.published = published;
    }

    /** As Unidades publicadas, por {@code order}. */
    public List<UnitStructure> units() {
        return this.published.structures();
    }

    /** A Unidade publicada; vazio se ela não tem versão publicada. */
    public Optional<UnitStructure> unit(String unitKey) {
        return units().stream().filter(unit -> unit.unitKey().equals(unitKey)).findFirst();
    }
}
