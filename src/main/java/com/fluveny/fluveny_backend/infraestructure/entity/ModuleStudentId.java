package com.fluveny.fluveny_backend.infraestructure.entity;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ModuleStudentId implements Serializable {
    private String moduleId;
    private String studentUserName;
}
