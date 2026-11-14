package com.fluveny.fluveny_backend.platform.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;

import io.sentry.Sentry;

/**
 * Substitui o {@code BasicErrorController} do Boot: o que chega ao
 * {@code /error} do container (erro fora do Spring MVC, por exemplo num filtro)
 * também sai como {@code application/problem+json}, nunca como HTML (AD-11).
 *
 * <ul>
 * <li>5xx com exceção: a exceção vai para o log e o Sentry, nunca para a resposta; sem exceção, só para o log;</li>
 * <li>401: leva {@code WWW-Authenticate}, como os da segurança;</li>
 * <li>sem status de erro (um {@code GET /error} direto): 404, porque não há erro a mostrar;</li>
 * <li>caminho que falta ou não é uma URI válida: o {@code instance} sai vazio.</li>
 * </ul>
 */
@RestController
@PublicRoute
class ProblemErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

	@RequestMapping("${server.error.path:${error.path:/error}}")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		HttpStatusCode status = status(request);
		Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		String uri = (path instanceof String value) ? value : null;
		if (status.is5xxServerError()) {
			if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable ex) {
				log.error("Erro inesperado fora do Spring MVC em {}", uri, ex);
				Sentry.captureException(ex);
			}
			else {
				log.error("Erro {} fora do Spring MVC em {}, sem exceção", status.value(), uri);
			}
		}
		ProblemDetail problem = Problems.forStatus(status);
		problem.setInstance(Problems.instanceOf(uri));
		ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON);
		if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
			response.header(HttpHeaders.WWW_AUTHENTICATE, Problems.BEARER_CHALLENGE);
		}
		return response.body(problem);
	}

	private static HttpStatusCode status(HttpServletRequest request) {
		Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (code instanceof Integer value && value >= 400 && value <= 599) {
			return HttpStatusCode.valueOf(value);
		}
		if (code == null) {
			return HttpStatus.NOT_FOUND;
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
