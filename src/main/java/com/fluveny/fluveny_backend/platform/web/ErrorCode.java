package com.fluveny.fluveny_backend.platform.web;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Códigos de erro transversais da {@code platform} (AD-11). Os módulos lançam os próprios códigos
 * de domínio por {@link ApiException}; estes cobrem o que é comum a toda rota.
 */
public enum ErrorCode {
    BAD_REQUEST(
            HttpStatus.BAD_REQUEST, "Requisição inválida", "A requisição não pôde ser processada."),
    VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos não são válidos."),
    INVALID_ID(
            HttpStatus.BAD_REQUEST,
            "Identificador inválido",
            "O identificador informado não é válido."),
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "Não autenticado",
            "É preciso entrar para acessar este recurso."),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "Acesso negado",
            "Você não tem permissão para acessar este recurso."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Não encontrado", "O recurso solicitado não existe."),
    CONFLICT(
            HttpStatus.CONFLICT,
            "Conflito",
            "A requisição conflita com o estado atual do recurso."),
    PAYLOAD_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "Conteúdo grande demais",
            "O corpo da requisição passa do tamanho aceito."),
    UNPROCESSABLE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Requisição não processável",
            "A requisição é válida, mas não pôde ser atendida."),
    TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "Muitas requisições",
            "Muitas requisições em pouco tempo. Aguarde e tente de novo."),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Método não permitido",
            "O método HTTP usado não é aceito neste recurso."),
    NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE,
            "Formato não aceito",
            "Não há resposta disponível no formato pedido."),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Formato não suportado",
            "O tipo de conteúdo enviado não é aceito."),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno",
            "Ocorreu um erro inesperado. Tente novamente mais tarde.");

    /** Títulos em pt-BR de status sem código próprio (ver {@link #titleFor}). */
    private static final Map<Integer, String> STATUS_TITLES =
            Map.of(
                    408,
                    "Tempo esgotado",
                    410,
                    "Recurso removido",
                    412,
                    "Pré-condição não atendida",
                    501,
                    "Não implementado",
                    502,
                    "Erro de comunicação",
                    503,
                    "Serviço indisponível",
                    504,
                    "Tempo esgotado");

    private static final String GENERIC_CLIENT_TITLE = "Erro na requisição";

    private static final String GENERIC_SERVER_TITLE = "Erro interno";

    private final HttpStatus status;

    private final String title;

    private final String detail;

    ErrorCode(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus status() {
        return this.status;
    }

    /** Título em pt-BR. */
    public String title() {
        return this.title;
    }

    /** Detalhe padrão em pt-BR. */
    public String detail() {
        return this.detail;
    }

    /**
     * O código genérico de um status: o primeiro com esse status, senão {@link #INTERNAL_ERROR}
     * para 5xx e {@link #BAD_REQUEST} para o resto. O título de um status sem código vem de {@link
     * #titleFor}, nunca do código genérico.
     */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        return exactly(status).orElse(status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST);
    }

    /**
     * O título em pt-BR de um status: o do código com esse status; senão o da tabela de status sem
     * código; senão "Erro interno" para 5xx e "Erro na requisição" para o resto. Nunca o título de
     * outro status.
     */
    public static String titleFor(HttpStatusCode status) {
        return exactly(status)
                .map(ErrorCode::title)
                .orElseGet(() ->
                        STATUS_TITLES.getOrDefault(
                                status.value(),
                                status.is5xxServerError()
                                        ? GENERIC_SERVER_TITLE
                                        : GENERIC_CLIENT_TITLE));
    }

    private static Optional<ErrorCode> exactly(HttpStatusCode status) {
        return Arrays.stream(values())
                .filter(code -> code.status.value() == status.value())
                .findFirst();
    }
}
