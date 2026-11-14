/**
 * Módulo `learning`: corretor, tentativas, Progresso, XP, conclusões, Badge e Visitante (AD-3,
 * AD-7, AD-9, AD-10).
 */
@ApplicationModule(
        allowedDependencies = {
            "content",
            "content :: activity",
            "content :: render",
            "account",
            "metrics",
            "platform :: *"
        })
package com.fluveny.fluveny_backend.learning;

import org.springframework.modulith.ApplicationModule;
