package com.fluveny.fluveny_backend.platform.web;

/**
 * {@code 429 TOO_MANY_REQUESTS} de um {@link RateLimiter}. O {@link ProblemHandler} põe o {@code
 * Retry-After} na resposta.
 */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.detail());
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Segundos até a próxima janela. */
    public long getRetryAfterSeconds() {
        return this.retryAfterSeconds;
    }
}
