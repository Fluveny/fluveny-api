package com.fluveny.fluveny_backend.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Envelope de sucesso da API: {@code {data, message?}} (AD-11). O {@code message} só aparece quando
 * não é nulo; o {@code data} sai sempre, mesmo nulo.
 *
 * @param data o corpo da resposta
 * @param message mensagem opcional ao usuário, em pt-BR
 * @param <T> o tipo de {@code data}
 */
public record ApiResponse<T>(
        @Schema(requiredMode = RequiredMode.REQUIRED) T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message);
    }
}
