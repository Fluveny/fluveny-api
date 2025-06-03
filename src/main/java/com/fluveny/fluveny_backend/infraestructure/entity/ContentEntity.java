package com.fluveny.fluveny_backend.infraestructure.entity;

import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Setter
@Getter
public class ContentEntity {
    @EqualsAndHashCode.Include
    private ContentType type;
    @EqualsAndHashCode.Include
    private String id;
}
