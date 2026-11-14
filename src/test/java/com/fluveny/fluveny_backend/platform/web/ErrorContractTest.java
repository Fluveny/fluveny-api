package com.fluveny.fluveny_backend.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

/** Códigos, títulos e {@link ApiException} (AD-11, Loop 1 da história 1.2). */
class ErrorContractTest {

	@Test
	void newCodesHaveTheirStatus() {
		assertThat(ErrorCode.fromStatus(HttpStatus.CONFLICT)).isEqualTo(ErrorCode.CONFLICT);
		assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(413))).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
		assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(422))).isEqualTo(ErrorCode.UNPROCESSABLE);
		assertThat(ErrorCode.fromStatus(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
	}

	@Test
	void domainCodeTakesTheTitleOfItsOwnStatus() {
		ApiException ex = new ApiException(HttpStatus.CONFLICT, "STAGE_LOCKED", "O estágio está bloqueado.");

		ProblemDetail problem = Problems.of(ex.getStatus(), ex.getCode(), ex.getDetail());

		assertThat(problem.getTitle()).isEqualTo(ErrorCode.CONFLICT.title());
		assertThat(problem.getProperties()).containsEntry("code", "STAGE_LOCKED");
	}

	@Test
	void statusWithoutCodeNeverBorrowsTheTitleOfAnotherStatus() {
		assertThat(ErrorCode.titleFor(HttpStatus.REQUEST_TIMEOUT)).isEqualTo("Tempo esgotado");
		assertThat(ErrorCode.titleFor(HttpStatus.I_AM_A_TEAPOT)).isEqualTo("Erro na requisição");
		assertThat(ErrorCode.titleFor(HttpStatusCode.valueOf(599))).isEqualTo("Erro interno");
		assertThat(Problems.forStatus(HttpStatus.I_AM_A_TEAPOT).getTitle()).isEqualTo("Erro na requisição")
			.isNotEqualTo(ErrorCode.BAD_REQUEST.title());
		assertThat(Problems.forStatus(HttpStatus.I_AM_A_TEAPOT).getStatus()).isEqualTo(418);
	}

	@Test
	void apiExceptionRejectsNullsAndInvalidCodes() {
		assertThatThrownBy(() -> new ApiException(null, "X", "d")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ApiException(HttpStatus.CONFLICT, null, "d"))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ApiException(HttpStatus.CONFLICT, "X", null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ApiException(HttpStatus.OK, "X", "d")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ApiException(HttpStatus.FOUND, "X", "d"))
			.isInstanceOf(IllegalArgumentException.class);
		for (String code : new String[] { "stage_locked", "Stage", "1CODE", "STAGE-LOCKED", "", "_X" }) {
			assertThatThrownBy(() -> new ApiException(HttpStatus.CONFLICT, code, "d")).as(code)
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void apiExceptionKeepsTheCause() {
		IllegalStateException cause = new IllegalStateException("x");

		ApiException ex = new ApiException(HttpStatus.CONFLICT, "STAGE_LOCKED_2", "d", cause);

		assertThat(ex.getCause()).isSameAs(cause);
		assertThat(ex.getCode()).isEqualTo("STAGE_LOCKED_2");
	}

	@Test
	void codeWithAnotherStatusTakesTheTitleOfTheStatus() {
		assertThat(Problems.of(HttpStatus.UNAUTHORIZED, ErrorCode.ACCESS_DENIED).getTitle())
			.isEqualTo(ErrorCode.UNAUTHENTICATED.title());
		assertThat(Problems.of(ErrorCode.ACCESS_DENIED).getTitle()).isEqualTo("Acesso negado");
	}

	@Test
	void nullDataIsSerialized() {
		assertThat(JsonMapper.builder().build().writeValueAsString(ApiResponse.of(null))).isEqualTo("{\"data\":null}");
		assertThat(JsonMapper.builder().build().writeValueAsString(ApiResponse.of("x", "ok")))
			.isEqualTo("{\"data\":\"x\",\"message\":\"ok\"}");
	}

	@Test
	void writeDoesNothingOnACommittedResponse() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.getWriter().write("parcial");
		response.flushBuffer();

		new Problems(JsonMapper.builder().build()).write(new MockHttpServletRequest("GET", "/x"), response,
				Problems.of(ErrorCode.NOT_FOUND));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString()).isEqualTo("parcial");
	}

	@Test
	void writeLeavesTheInstanceEmptyWhenThePathIsNotAUri() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a b{}");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new Problems(JsonMapper.builder().build()).write(request, response, Problems.of(ErrorCode.NOT_FOUND));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(response.getContentAsString()).contains("\"code\":\"NOT_FOUND\"")
			.doesNotContain("instance");
	}

}
