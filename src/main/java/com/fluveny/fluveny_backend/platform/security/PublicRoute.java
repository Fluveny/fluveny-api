package com.fluveny.fluveny_backend.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara uma rota pública, sem autenticação (AD-12). Vale no método ou na classe do controller; a
 * declaração do método vence a da classe. Toda rota declara {@code @PublicRoute} ou {@link
 * RequiresRole}; sem declaração, ela é negada.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicRoute {}
