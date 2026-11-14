package com.fluveny.fluveny_backend.platform.web;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Monta os {@link ProblemDetail} da API (RFC 9457, AD-11) e os escreve fora do
 * Spring MVC, para os filtros de segurança e o controller de erro. Pública só
 * para a própria {@code platform} ({@code security}); os módulos lançam
 * {@link ApiException}.
 */
@Component
public class Problems {

	static final String TYPE_PREFIX = "urn:fluveny:problem:";

	/** Valor do {@code WWW-Authenticate} de todo 401 (RFC 9110, §11.6.1). */
	public static final String BEARER_CHALLENGE = "Bearer realm=\"fluveny\"";

	private final JsonMapper jsonMapper;

	Problems(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** Problema com o status, o título e o detalhe padrão do código. */
	public static ProblemDetail of(ErrorCode code) {
		return of(code.status(), code, code.detail());
	}

	/** Problema com um status diferente do padrão do código (por exemplo, 401 com {@code ACCESS_DENIED}). */
	public static ProblemDetail of(HttpStatusCode status, ErrorCode code) {
		return of(status, code, code.detail());
	}

	/**
	 * O título é o do código quando o status é o padrão dele; senão, o do status
	 * ({@link ErrorCode#titleFor}). Um 401 com {@code ACCESS_DENIED} sai com o
	 * título do 401.
	 */
	public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
		String title = (code.status().value() == status.value()) ? code.title() : ErrorCode.titleFor(status);
		return of(status, code.name(), title, detail);
	}

	/** Problema de um código de domínio; o título vem do status ({@link ErrorCode#titleFor}). */
	public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
		return of(status, code, ErrorCode.titleFor(status), detail);
	}

	/**
	 * Problema genérico de um status: o código de {@link ErrorCode#fromStatus},
	 * com o título do próprio status. O detalhe é o do código quando ele é desse
	 * status, senão o genérico do lado (cliente ou servidor).
	 */
	public static ProblemDetail forStatus(HttpStatusCode status) {
		ErrorCode code = ErrorCode.fromStatus(status);
		return of(status, code.name(), ErrorCode.titleFor(status), code.detail());
	}

	private static final URI EMPTY_INSTANCE = URI.create("");

	/**
	 * O caminho como {@code instance}; vazio se faltar ou não for uma URI válida.
	 * Nunca lança, e nunca deixa nulo (o Spring MVC preencheria com outro
	 * caminho). Vazio, o campo é omitido do JSON.
	 */
	static URI instanceOf(String path) {
		if (path == null) {
			return EMPTY_INSTANCE;
		}
		try {
			return new URI(path);
		}
		catch (URISyntaxException ex) {
			return EMPTY_INSTANCE;
		}
	}

	private static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

	/** Escreve o problema como {@code application/problem+json} direto na resposta. */
	public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
			throws IOException {
		if (response.isCommitted()) {
			return;
		}
		if (problem.getInstance() == null) {
			problem.setInstance(instanceOf(request.getRequestURI()));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", problem.getType().toString());
		body.put("title", problem.getTitle());
		body.put("status", problem.getStatus());
		body.put("detail", problem.getDetail());
		String instance = problem.getInstance().toString();
		if (!instance.isEmpty()) {
			// Vazio sai omitido, como o Jackson faz com o ProblemDetail no Spring MVC.
			body.put("instance", instance);
		}
		if (problem.getProperties() != null) {
			body.putAll(problem.getProperties());
		}
		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
