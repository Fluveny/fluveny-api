package com.fluveny.fluveny_backend.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara o papel exigido por uma rota (AD-12): basta ter um dos papéis, direto ou pela hierarquia
 * ({@code ADMIN} entra em rota de {@code CONTENT_CREATOR}). Vale no método ou na classe; a
 * declaração do método vence a da classe.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {

    Role[] value();
}
