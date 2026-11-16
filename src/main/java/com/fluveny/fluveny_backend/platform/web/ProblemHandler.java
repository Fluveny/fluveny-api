package com.fluveny.fluveny_backend.platform.web;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Transforma toda exceção do Spring MVC em {@code application/problem+json} com {@code code}
 * (AD-11, NFR5). Título e detalhe sempre em pt-BR; a mensagem da exceção e o stack trace nunca vão
 * para a resposta, só para o log e o Sentry.
 *
 * <p>Conversão: só um parâmetro do tipo {@link ObjectId} malformado vira {@code INVALID_ID}. Outro
 * tipo (número, enum, data) vira {@code VALIDATION_FAILED} com o nome do parâmetro em {@code
 * errors}. Uma falha de conversão fora do binding da requisição é bug do servidor: 500.
 *
 * <p>Status: só o 500 vai para o log e o Sentry; outro status (um 503 de timeout, um
 * {@code @ResponseStatus}) sai com o próprio código e título. Parâmetro, cabeçalho ou parte
 * obrigatórios que faltam viram {@code VALIDATION_FAILED} com o nome em {@code errors}.
 */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);

    static final String INVALID_PARAMETER_MESSAGE = "Valor inválido para este parâmetro.";

    static final String REQUIRED_MESSAGE = "Campo obrigatório.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error(
                    "Erro de domínio {} em {} {}",
                    ex.getCode(),
                    request.getMethod(),
                    request.getRequestURI(),
                    ex);
            Sentry.captureException(ex);
        } else if (ex.getCause() != null) {
            log.warn(
                    "Erro de domínio {} em {} {}",
                    ex.getCode(),
                    request.getMethod(),
                    request.getRequestURI(),
                    ex);
        }
        ProblemDetail problem = Problems.of(ex.getStatus(), ex.getCode(), ex.getDetail());
        ex.getProperties().forEach(problem::setProperty);
        HttpHeaders headers = new HttpHeaders();
        if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
            // Todo 401 leva o desafio (AD-12), inclusive o de domínio.
            headers.set(HttpHeaders.WWW_AUTHENTICATE, Problems.BEARER_CHALLENGE);
        }
        if (ex instanceof RateLimitedException limited) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(limited.getRetryAfterSeconds()));
        }
        return problem(problem, headers);
    }

    /** Negação lançada dentro do handler (por exemplo, a posse do recurso, AD-12). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return problem(Problems.of(ErrorCode.ACCESS_DENIED), new HttpHeaders());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, Problems.BEARER_CHALLENGE);
        return problem(Problems.of(ErrorCode.UNAUTHENTICATED), headers);
    }

    /**
     * O resto. Uma exceção anotada com {@link ResponseStatus} sai com esse status (o 500 continua
     * indo para o log e o Sentry); sem anotação, é 500.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        ResponseStatus annotated =
                AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (annotated != null && annotated.code() != HttpStatus.INTERNAL_SERVER_ERROR) {
            return problem(Problems.forStatus(annotated.code()), new HttpHeaders());
        }
        return problem(
                internalError(ex, request.getMethod(), request.getRequestURI()), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (request instanceof ServletWebRequest servlet
                && servlet.getResponse() != null
                && servlet.getResponse().isCommitted()) {
            // Como na classe base: com a resposta já enviada, não há o que escrever.
            log.warn("Resposta já enviada; problema descartado: {}", ex.toString());
            return null;
        }
        ProblemDetail problem =
                switch (ex) {
                    case NoResourceFoundException e -> Problems.of(ErrorCode.NOT_FOUND);
                    case NoHandlerFoundException e -> Problems.of(ErrorCode.NOT_FOUND);
                    case ConversionNotSupportedException e ->
                            internalError(ex, null, request.getDescription(false));
                    case MethodArgumentTypeMismatchException e ->
                            isObjectId(e)
                                    ? Problems.of(ErrorCode.INVALID_ID)
                                    : validationFailed(List.of(error(e.getName(), null)));
                    case TypeMismatchException e ->
                            validationFailed(List.of(error(e.getPropertyName(), null)));
                    case MethodArgumentNotValidException e -> validationFailed(fieldErrors(e));
                    case HandlerMethodValidationException e -> validationFailed(parameterErrors(e));
                    case MissingServletRequestParameterException e -> missing(e.getParameterName());
                    case MissingRequestHeaderException e -> missing(e.getHeaderName());
                    case MissingServletRequestPartException e -> missing(e.getRequestPartName());
                    // JSON malformado: não há campo a apontar, então errors vem vazio.
                    case HttpMessageNotReadableException e -> validationFailed(List.of());
                    default ->
                            statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()
                                    ? internalError(ex, null, request.getDescription(false))
                                    : Problems.forStatus(statusCode);
                };
        return problem(problem, headers);
    }

    /** {@code ObjectId} direto ou dentro de {@code Optional}. */
    private static boolean isObjectId(MethodArgumentTypeMismatchException ex) {
        return ObjectId.class.equals(ex.getRequiredType())
                || ObjectId.class.equals(
                        ex.getParameter().nestedIfOptional().getNestedParameterType());
    }

    private static ProblemDetail missing(String name) {
        return validationFailed(
                List.of(Map.of("field", name == null ? "" : name, "message", REQUIRED_MESSAGE)));
    }

    private ProblemDetail internalError(Exception ex, String method, String path) {
        log.error("Erro inesperado em {} {}", method == null ? "" : method, path, ex);
        Sentry.captureException(ex);
        return Problems.of(ErrorCode.INTERNAL_ERROR);
    }

    private static ProblemDetail validationFailed(List<Map<String, String>> errors) {
        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(error(error.getField(), error));
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> errors.add(error(error.getObjectName(), error)));
        return errors;
    }

    private static List<Map<String, String>> parameterErrors(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults()
                .forEach(result -> {
                    String field = result.getMethodParameter().getParameterName();
                    result.getResolvableErrors().forEach(error -> errors.add(error(field, error)));
                });
        return errors;
    }

    private static Map<String, String> error(String field, MessageSourceResolvable error) {
        String message =
                (error == null || error.getDefaultMessage() == null)
                        ? INVALID_PARAMETER_MESSAGE
                        : error.getDefaultMessage();
        return Map.of("field", field == null ? "" : field, "message", message);
    }

    private static ResponseEntity<Object> problem(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(HttpStatusCode.valueOf(problem.getStatus()))
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
