/**
 * API pública da {@code platform} para os outros módulos: envelope {@code ApiResponse}, erros
 * {@code ApiException}/{@code ErrorCode}, {@code SearchTerms} e o limite de taxa ({@code
 * RateLimiter}, {@code RateLimitedException}). {@code Problems} também é pública, mas só para a
 * própria {@code platform} (os 401/403 da {@code security}); os módulos lançam {@code
 * ApiException}. O resto ({@code ProblemHandler}, {@code ProblemErrorController}, {@code
 * ObjectIdConverter}) é interno.
 */
@NamedInterface("web")
package com.fluveny.fluveny_backend.platform.web;

import org.springframework.modulith.NamedInterface;
