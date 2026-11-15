package com.fluveny.fluveny_backend.platform.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Base dos erros de domínio (AD-11). O {@link ProblemHandler} transforma em {@code
 * application/problem+json} com o {@code code} e o {@code detail} dados aqui. Um 5xx vai para o log
 * e o Sentry; um 4xx com causa, para o log em {@code WARN}.
 *
 * <pre>{@code
 * throw new ApiException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND", "A atividade não existe.");
 * }</pre>
 */
public class ApiException extends RuntimeException {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final HttpStatus status;

    private final String code;

    private final String detail;

    /** Membros que o problema não pode ter como propriedade extra (RFC 9457 e o {@code code}). */
    private static final Set<String> RESERVED =
            Set.of("type", "title", "status", "detail", "instance", "code");

    private final Map<String, Object> properties;

    /**
     * @param status o status HTTP da resposta
     * @param code código estável em SCREAMING_SNAKE, que o App mapeia para texto
     * @param detail detalhe em pt-BR, exibível ao usuário
     * @throws NullPointerException se algum argumento for nulo
     * @throws IllegalArgumentException se {@code status} não for 4xx/5xx ou {@code code} não for
     *     SCREAMING_SNAKE
     */
    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, (Throwable) null);
    }

    /**
     * Como {@link #ApiException(HttpStatus, String, String)}, guardando a causa para o log. A causa
     * nunca vai para a resposta.
     */
    public ApiException(HttpStatus status, String code, String detail, Throwable cause) {
        super(validCode(code), cause);
        this.status = validStatus(status);
        this.code = code;
        this.detail = Objects.requireNonNull(detail, "detail");
        this.properties = Map.of();
    }

    /**
     * Como {@link #ApiException(HttpStatus, String, String)}, com membros extras no problema (por
     * exemplo, {@code errors}).
     *
     * @throws IllegalArgumentException se um nome for de membro padrão ({@code type}, {@code
     *     code}…)
     */
    public ApiException(HttpStatus status, String code, String detail, Map<String, ?> properties) {
        super(validCode(code));
        this.status = validStatus(status);
        this.code = code;
        this.detail = Objects.requireNonNull(detail, "detail");
        for (String name : properties.keySet()) {
            if (RESERVED.contains(name)) {
                throw new IllegalArgumentException("Membro reservado do problema: " + name);
            }
        }
        this.properties = Map.copyOf(new LinkedHashMap<>(properties));
    }

    /** Atalho para um dos códigos transversais, com detalhe próprio. */
    public ApiException(ErrorCode code, String detail) {
        this(code.status(), code.name(), detail);
    }

    private static HttpStatus validStatus(HttpStatus status) {
        Objects.requireNonNull(status, "status");
        if (!status.isError()) {
            throw new IllegalArgumentException("status deve ser 4xx ou 5xx: " + status.value());
        }
        return status;
    }

    private static String validCode(String code) {
        Objects.requireNonNull(code, "code");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "code deve ser SCREAMING_SNAKE ([A-Z][A-Z0-9_]*): " + code);
        }
        return code;
    }

    public HttpStatus getStatus() {
        return this.status;
    }

    public String getCode() {
        return this.code;
    }

    public String getDetail() {
        return this.detail;
    }

    /** Membros extras do problema; vazio quando não há. */
    public Map<String, Object> getProperties() {
        return this.properties;
    }
}
