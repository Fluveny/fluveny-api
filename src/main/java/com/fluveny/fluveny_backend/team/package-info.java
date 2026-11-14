/**
 * Módulo `team`: casos de uso do Administrador e criação do primeiro Administrador (AD-3, AD-13).
 */
@ApplicationModule(allowedDependencies = {"account", "auth", "platform :: *"})
package com.fluveny.fluveny_backend.team;

import org.springframework.modulith.ApplicationModule;
