package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Deny-by-default sem liberar por omissão (AD-12, Loop 1 da história 1.2):
 * só "nenhum handler casou" e 405/415/406 seguem para o Spring MVC; o resto
 * é negado.
 */
@ExtendWith(OutputCaptureExtension.class)
class RouteAuthorizationManagerTest {

	private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("k", "anon",
			AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private static final Authentication ADMIN = new TestingAuthenticationToken("a", "x", Role.ADMIN.authority());

	@Test
	void noMatchingHandlerGoesOnToTheNotFound() throws Exception {
		assertThat(decide(mappingReturning(null), ANONYMOUS, "/api/v1/nada")).isTrue();
	}

	@Test
	void firstMappingWithAHandlerWins() throws Exception {
		HandlerMapping none = mappingReturning(null);
		HandlerMapping undeclared = mappingReturning(new HandlerExecutionChain(handler(Undeclared.class)));
		HandlerMapping open = mappingReturning(new HandlerExecutionChain(handler(Open.class)));

		assertThat(decide(List.of(none, undeclared, open), ADMIN, "/x")).isFalse();
		assertThat(decide(List.of(none, open, undeclared), ANONYMOUS, "/x")).isTrue();
	}

	@Test
	void handlerThatIsNotAMethodIsDenied(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = request("/test/router");
		HandlerMapping mapping = mappingReturning(new HandlerExecutionChain(new Object()));

		assertThat(decide(List.of(mapping), ADMIN, request)).isFalse();
		assertThat(request.getAttribute(RouteAuthorizationManager.UNDECLARED_ROUTE)).asString()
			.startsWith("NOT_A_HANDLER_METHOD");
		assertThat(output).contains("não é um método de controller");
	}

	@Test
	void handlerThatIsNotAMethodIsAllowedOnTheApiDocsPaths() throws Exception {
		HandlerMapping mapping = mappingReturning(new HandlerExecutionChain(new Object()));

		for (String path : Set.of("/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs.yaml")) {
			assertThat(decide(List.of(mapping), ANONYMOUS, path)).as(path).isTrue();
		}
	}

	@Test
	void methodMediaAndAcceptFailuresCountAsNoHandler() throws Exception {
		assertThat(decide(mappingThrowing(new HttpRequestMethodNotSupportedException("DELETE")), ANONYMOUS, "/x"))
			.isTrue();
		assertThat(decide(mappingThrowing(new HttpMediaTypeNotSupportedException("text/plain")), ANONYMOUS, "/x"))
			.isTrue();
		assertThat(decide(mappingThrowing(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON))),
				ANONYMOUS, "/x"))
			.isTrue();
		assertThat(decide(mappingThrowing(new UnsatisfiedServletRequestParameterException(new String[] { "a=1" },
				java.util.Map.of())), ANONYMOUS, "/x"))
			.isTrue();
	}

	@Test
	void anyOtherResolutionFailureIsDeniedAndLogged(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = request("/x");

		assertThat(decide(List.of(mappingThrowing(new IllegalStateException("quebrou"))), ADMIN, request)).isFalse();
		assertThat(request.getAttribute(RouteAuthorizationManager.UNDECLARED_ROUTE)).isEqualTo("RESOLUTION_FAILED");
		assertThat(output).contains("falha ao resolver o handler").contains("quebrou");
	}

	@Test
	void logSeparatesUndeclaredFromConflicting(CapturedOutput output) throws Exception {
		MockHttpServletRequest undeclared = request("/u");
		MockHttpServletRequest conflicting = request("/c");

		assertThat(decide(List.of(mappingReturning(new HandlerExecutionChain(handler(Undeclared.class)))), ADMIN,
				undeclared))
			.isFalse();
		assertThat(decide(List.of(mappingReturning(new HandlerExecutionChain(handler(Conflicting.class)))), ADMIN,
				conflicting))
			.isFalse();

		assertThat(undeclared.getAttribute(RouteAuthorizationManager.UNDECLARED_ROUTE)).asString()
			.startsWith("UNDECLARED ");
		assertThat(conflicting.getAttribute(RouteAuthorizationManager.UNDECLARED_ROUTE)).asString()
			.startsWith("CONFLICTING ");
		assertThat(output).contains("(UNDECLARED)").contains("(CONFLICTING)");
	}

	@Test
	void resolutionLeavesNoAttributesOnTheRequest() throws Exception {
		MockHttpServletRequest request = request("/x");
		HandlerMapping mapping = mock(HandlerMapping.class);
		when(mapping.getHandler(any())).thenAnswer(invocation -> {
			invocation.<jakarta.servlet.http.HttpServletRequest>getArgument(0).setAttribute("trace", "x");
			return new HandlerExecutionChain(handler(Open.class));
		});

		assertThat(decide(List.of(mapping), ANONYMOUS, request)).isTrue();
		assertThat(request.getAttribute("trace")).isNull();
	}

	private static boolean decide(HandlerMapping mapping, Authentication authentication, String path) {
		return decide(List.of(mapping), authentication, request(path));
	}

	private static boolean decide(List<HandlerMapping> mappings, Authentication authentication, String path) {
		return decide(mappings, authentication, request(path));
	}

	private static boolean decide(List<HandlerMapping> mappings, Authentication authentication,
			MockHttpServletRequest request) {
		RouteAuthorizationManager manager = new RouteAuthorizationManager(() -> mappings,
				RoleHierarchyConfig.roleHierarchy());
		return manager.authorize(() -> authentication, new RequestAuthorizationContext(request)).isGranted();
	}

	private static MockHttpServletRequest request(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setServletPath(path);
		return request;
	}

	private static HandlerMapping mappingReturning(HandlerExecutionChain chain) throws Exception {
		HandlerMapping mapping = mock(HandlerMapping.class);
		when(mapping.getHandler(any())).thenReturn(chain);
		return mapping;
	}

	private static HandlerMapping mappingThrowing(Exception ex) throws Exception {
		HandlerMapping mapping = mock(HandlerMapping.class);
		when(mapping.getHandler(any())).thenThrow(ex);
		return mapping;
	}

	private static HandlerMethod handler(Class<?> type) throws Exception {
		return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getDeclaredMethod("get"));
	}

	static class Undeclared {

		String get() {
			return "";
		}

	}

	@PublicRoute
	static class Open {

		String get() {
			return "";
		}

	}

	static class Conflicting {

		@PublicRoute
		@RequiresRole(Role.ADMIN)
		String get() {
			return "";
		}

	}

}
