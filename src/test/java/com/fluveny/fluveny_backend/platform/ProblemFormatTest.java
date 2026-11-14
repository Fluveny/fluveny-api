package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.sentry.Sentry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Envelope e formato de problema (AD-11, NFR5): linhas da matriz da história 1.2. */
@PlatformWebTest
@ExtendWith(OutputCaptureExtension.class)
class ProblemFormatTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void wrapsSuccessInTheEnvelope() throws Exception {
		this.mvc.perform(get("/test/open/x/{id}", "65f1c0ffee0000000000abcd"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value("65f1c0ffee0000000000abcd"))
			.andExpect(jsonPath("$.message").doesNotExist());
	}

	@Test
	void includesTheMessageWhenGiven() throws Exception {
		this.mvc.perform(post("/test/open/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alex\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value("Alex"))
			.andExpect(jsonPath("$.message").value("Recebido."));
	}

	@Test
	void invalidIdIsBadRequest() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/x/{id}", "abc")), 400, "INVALID_ID");
	}

	@Test
	void unknownRouteIsNotFound() throws Exception {
		expectProblem(this.mvc.perform(get("/api/v1/nada")), 404, "NOT_FOUND");
	}

	@Test
	void malformedJsonIsValidationFailed() throws Exception {
		expectProblem(this.mvc.perform(post("/test/open/body").contentType(MediaType.APPLICATION_JSON).content("{nome")),
				400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors").isArray());
	}

	@Test
	void beanValidationFailureListsTheFields() throws Exception {
		expectProblem(this.mvc.perform(post("/test/open/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")),
				400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			// Mensagem da Bean Validation em pt-BR (locale fixo); o Accept-Language fica no teste abaixo.
			.andExpect(jsonPath("$.errors[0].message").value("não deve estar em branco"));
	}

	@Test
	void validationMessagesIgnoreTheAcceptLanguage() throws Exception {
		this.mvc
			.perform(post("/test/open/body").contentType(MediaType.APPLICATION_JSON)
				.header("Accept-Language", "en-US")
				.content("{\"name\":\"\"}"))
			.andExpect(jsonPath("$.errors[0].message").value("não deve estar em branco"));
	}

	@Test
	void unexpectedExceptionIsInternalErrorWithoutDetails(CapturedOutput output) throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/boom")), 500, "INTERNAL_ERROR")
			.andExpect(content().string(not(containsString("segredo"))))
			.andExpect(content().string(not(containsString("IllegalStateException"))))
			.andExpect(content().string(not(containsString("at com.fluveny"))));

		// O detalhe fica só no log (e no Sentry, quando ligado).
		assertThat(output).contains("Erro inesperado em").contains("segredo interno 1234");

		// Sem SENTRY_DSN, o Sentry fica desligado e o erro não derruba nada.
		assertThat(Sentry.isEnabled()).isFalse();
	}

	@Test
	void logsAreStructuredEcsJson(CapturedOutput output) throws Exception {
		this.mvc.perform(get("/test/open/boom"));

		String line = output.getOut()
			.lines()
			.filter(candidate -> candidate.contains("Erro inesperado em GET /test/open/boom"))
			.findFirst()
			.orElseThrow();
		JsonNode log = JsonMapper.builder().build().readTree(line);
		assertThat(log.path("@timestamp").isString()).isTrue();
		assertThat(log.at("/log/level").asString()).isEqualTo("ERROR");
		assertThat(log.path("message").asString()).startsWith("Erro inesperado em GET /test/open/boom");
	}

	@Test
	void serverDomainExceptionIsLoggedWithItsOwnStatus(CapturedOutput output) throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/domain-server")), 502, "UPSTREAM_DOWN");

		assertThat(output).contains("Erro de domínio UPSTREAM_DOWN em GET /test/open/domain-server");
	}

	@Test
	void clientDomainExceptionWithCauseIsLoggedAsWarning(CapturedOutput output) throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/domain-cause")), 409, "STAGE_LOCKED")
			.andExpect(content().string(not(containsString("555"))));

		String line = output.getOut()
			.lines()
			.filter(candidate -> candidate.contains("Erro de domínio STAGE_LOCKED em GET /test/open/domain-cause"))
			.findFirst()
			.orElseThrow();
		assertThat(line).contains("\"WARN\"").contains("causa conhecida 555");
	}

	@Test
	void nullDataStaysInTheEnvelope() throws Exception {
		this.mvc.perform(get("/test/open/null-data"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"data\":null}", JsonCompareMode.STRICT));
	}

	@Test
	void methodParameterValidationListsTheParameter() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/search").param("q", "abcdef")), 400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("q"))
			.andExpect(jsonPath("$.errors[0].message").value("tamanho deve ser entre 0 e 3"));
	}

	@Test
	void missingParameterHeaderAndPartAreValidationFailedWithTheName() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/page")), 400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("page"))
			.andExpect(jsonPath("$.errors[0].message").value("Campo obrigatório."));
		expectProblem(this.mvc.perform(get("/test/open/header")), 400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("X-Trace"));
		expectProblem(this.mvc.perform(multipart("/test/open/part")), 400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("file"));
	}

	@Test
	void optionalObjectIdIsInvalidIdToo() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/optional/{id}", "abc")), 400, "INVALID_ID");
	}

	@Test
	void responseStatusAnnotationKeepsItsStatus() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/gone")), 410, "BAD_REQUEST")
			.andExpect(jsonPath("$.title").value("Recurso removido"));
	}

	@Test
	void serverErrorOtherThan500KeepsItsStatus() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/timeout")), 503, "INTERNAL_ERROR")
			.andExpect(jsonPath("$.title").value("Serviço indisponível"));
	}

	@Test
	void uploadTooLargeIsPayloadTooLarge() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/too-large")), 413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	void domainExceptionKeepsItsCodeAndDetail() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/domain")), 409, "STAGE_LOCKED")
			.andExpect(jsonPath("$.title").value("Conflito"))
			.andExpect(jsonPath("$.detail").value("O estágio está bloqueado."));
	}

	@Test
	void nonObjectIdTypeMismatchIsValidationFailedNamingTheParameter() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/page").param("page", "abc")), 400, "VALIDATION_FAILED")
			.andExpect(jsonPath("$.errors[0].field").value("page"))
			.andExpect(jsonPath("$.errors[0].message").isString());
	}

	@Test
	void conversionFailureOutsideTheBindingIsInternalError() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/conversion")), 500, "INTERNAL_ERROR");
	}

	@Test
	void accessDeniedInsideTheHandlerIsForbidden() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/denied")), 403, "ACCESS_DENIED")
			.andExpect(content().string(not(containsString("dono"))));
	}

	@Test
	void authenticationFailureInsideTheHandlerIsUnauthorized() throws Exception {
		expectProblem(this.mvc.perform(get("/test/open/unauthenticated")), 401, "UNAUTHENTICATED")
			.andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""))
			.andExpect(content().string(not(containsString("expirada"))));
	}

	@Test
	void unsupportedMediaTypeIsAProblem() throws Exception {
		expectProblem(this.mvc.perform(post("/test/open/body").contentType(MediaType.TEXT_PLAIN).content("x")), 415,
				"UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	void notAcceptableIsAProblem() throws Exception {
		expectProblem(
				this.mvc.perform(get("/test/open/x/{id}", "65f1c0ffee0000000000abcd").accept(MediaType.APPLICATION_XML)),
				406, "NOT_ACCEPTABLE");
	}

	@Test
	void directGetOnTheErrorPathIsNotFound() throws Exception {
		expectProblem(this.mvc.perform(get("/error")), 404, "NOT_FOUND");
	}

	@Test
	void containerUnauthorizedCarriesTheChallenge() throws Exception {
		expectProblem(this.mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401)), 401,
				"UNAUTHENTICATED")
			.andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
	}

	@Test
	void containerServerErrorWithoutExceptionIsLogged(CapturedOutput output) throws Exception {
		expectProblem(this.mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)
			.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/filtro")), 503, "INTERNAL_ERROR");

		assertThat(output).contains("Erro 503 fora do Spring MVC em /api/v1/filtro, sem exceção");
	}

	@Test
	void containerServerErrorIsLoggedAndTheInvalidPathLeavesTheInstanceEmpty(CapturedOutput output) throws Exception {
		expectProblem(this.mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
			.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/a b")
			.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("falha no filtro 987"))), 500,
				"INTERNAL_ERROR")
			// instance vazio: omitido do JSON, e nunca o caminho /error.
			.andExpect(jsonPath("$.instance").doesNotExist())
			.andExpect(content().string(not(containsString("987"))));

		assertThat(output).contains("Erro inesperado fora do Spring MVC").contains("falha no filtro 987");
	}

	@Test
	void wrongMethodIsMethodNotAllowed() throws Exception {
		expectProblem(this.mvc.perform(post("/test/open/boom")), 405, "METHOD_NOT_ALLOWED");
	}

	static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
		return result.andExpect(status().is(status))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value(not(empty())))
			.andExpect(jsonPath("$.title").isString())
			.andExpect(jsonPath("$.status").value(status))
			.andExpect(jsonPath("$.detail").isString())
			.andExpect(jsonPath("$.code").value(code));
	}

}
