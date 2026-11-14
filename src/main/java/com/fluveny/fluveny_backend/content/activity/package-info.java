/**
 * API pública do {@code content} para os outros módulos (AD-3, AD-6): as famílias
 * {@link com.fluveny.fluveny_backend.content.activity.Activity},
 * {@link com.fluveny.fluveny_backend.content.activity.ActivityStudentView} e
 * {@link com.fluveny.fluveny_backend.content.activity.Answer}, com um record por
 * Tipo, e o {@link com.fluveny.fluveny_backend.content.activity.ContentText}.
 * O formato vem do JSON Schema de {@code content/schema/}; o
 * {@code ActivityFidelityTest} falha o build se os dois divergirem.
 */
@NamedInterface("activity")
package com.fluveny.fluveny_backend.content.activity;

import org.springframework.modulith.NamedInterface;
