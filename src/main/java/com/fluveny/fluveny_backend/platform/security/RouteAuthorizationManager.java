package com.fluveny.fluveny_backend.platform.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.PathContainer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Autorização única da API, deny-by-default (AD-12). Resolve o handler da
 * requisição como o {@code DispatcherServlet} faria (todos os
 * {@link HandlerMapping}, na ordem) e lê a declaração dele ({@link PublicRoute}
 * ou {@link RequiresRole}):
 * <ul>
 * <li>nenhum handler casou: libera, e o Spring MVC responde 404 (não há rota a proteger).
 * O mesmo vale para 405, 415, 406 e o 400 de parâmetro exigido pelo mapeamento, em que nenhum handler roda;</li>
 * <li>handler que não é {@link HandlerMethod} (um {@code RouterFunction}, um recurso): nega,
 * a menos que o caminho seja da documentação ({@link SecurityConfig#API_DOCS_PATHS});</li>
 * <li>qualquer outra exceção na resolução: nega e loga;</li>
 * <li>handler sem declaração ou com declaração conflitante: nega, e o motivo fica em
 * {@link #UNDECLARED_ROUTE};</li>
 * <li>{@code @PublicRoute}: libera;</li>
 * <li>{@code @RequiresRole}: libera se o usuário autenticado alcança um dos papéis pela hierarquia.</li>
 * </ul>
 */
@Component
class RouteAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	/** Atributo da requisição negada por falta de declaração válida; o valor diz o motivo. */
	static final String UNDECLARED_ROUTE = RouteAuthorizationManager.class.getName() + ".UNDECLARED_ROUTE";

	private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationManager.class);

	private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);

	private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

	/** Exceções da resolução em que nenhum handler roda e o Spring MVC responde o problema certo (405, 415, 406, 400). */
	private static final List<Class<? extends Exception>> NO_HANDLER_EXCEPTIONS = List.of(
			HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
			HttpMediaTypeNotAcceptableException.class, UnsatisfiedServletRequestParameterException.class);

	private static final List<PathPattern> API_DOCS_PATTERNS = Arrays.stream(SecurityConfig.API_DOCS_PATHS)
		.map(PathPatternParser.defaultInstance::parse)
		.toList();

	private final Supplier<List<HandlerMapping>> handlerMappings;

	private final RoleHierarchy roleHierarchy;

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	@Autowired
	RouteAuthorizationManager(ObjectProvider<HandlerMapping> handlerMappings, RoleHierarchy roleHierarchy) {
		this(SingletonSupplier.of(() -> handlerMappings.orderedStream().toList()), roleHierarchy);
	}

	RouteAuthorizationManager(Supplier<List<HandlerMapping>> handlerMappings, RoleHierarchy roleHierarchy) {
		this.handlerMappings = handlerMappings;
		this.roleHierarchy = roleHierarchy;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		HttpServletRequest request = context.getRequest();
		IsolatedAttributesRequest isolated = new IsolatedAttributesRequest(request);
		Object handler;
		try {
			handler = resolveHandler(isolated);
		}
		catch (Exception ex) {
			if (NO_HANDLER_EXCEPTIONS.stream().anyMatch(type -> type.isInstance(ex))) {
				return GRANTED;
			}
			log.warn("Rota negada: falha ao resolver o handler de {} {}", request.getMethod(), request.getRequestURI(),
					ex);
			return deny(request, "RESOLUTION_FAILED");
		}
		if (handler == null) {
			return GRANTED;
		}
		if (!(handler instanceof HandlerMethod method)) {
			if (isApiDocs(isolated)) {
				return GRANTED;
			}
			log.warn("Rota negada: o handler {} não é um método de controller e não pode declarar papel", handler);
			return deny(request, "NOT_A_HANDLER_METHOD " + handler);
		}
		RouteDeclaration declaration = RouteDeclaration.of(method);
		return switch (declaration.kind()) {
			case PUBLIC -> GRANTED;
			case ROLES -> hasAnyRole(authentication.get(), declaration) ? GRANTED : DENIED;
			case UNDECLARED -> {
				log.warn("Rota negada (UNDECLARED): o handler {} não declara @PublicRoute nem @RequiresRole", method);
				yield deny(request, "UNDECLARED " + method);
			}
			case CONFLICTING -> {
				log.warn("Rota negada (CONFLICTING): o handler {} declara @PublicRoute e @RequiresRole juntos, "
						+ "ou @RequiresRole sem papel", method);
				yield deny(request, "CONFLICTING " + method);
			}
		};
	}

	private static AuthorizationDecision deny(HttpServletRequest request, String reason) {
		request.setAttribute(UNDECLARED_ROUTE, reason);
		return DENIED;
	}

	private boolean hasAnyRole(Authentication authentication, RouteDeclaration declaration) {
		if (!this.trustResolver.isAuthenticated(authentication)) {
			return false;
		}
		Set<String> reachable = new HashSet<>();
		for (GrantedAuthority authority : this.roleHierarchy
			.getReachableGrantedAuthorities(authentication.getAuthorities())) {
			reachable.add(authority.getAuthority());
		}
		return declaration.roles().stream().map(Role::authority).anyMatch(reachable::contains);
	}

	/**
	 * Resolve o handler sem deixar rastro na requisição: o mapeamento grava
	 * atributos (caminho, variáveis), que ficam só no invólucro. O primeiro
	 * mapeamento que devolve um handler vence, como no {@code DispatcherServlet}.
	 */
	private Object resolveHandler(IsolatedAttributesRequest isolated) throws Exception {
		ServletRequestPathUtils.parseAndCache(isolated);
		for (HandlerMapping mapping : this.handlerMappings.get()) {
			HandlerExecutionChain chain = mapping.getHandler(isolated);
			if (chain != null) {
				return chain.getHandler();
			}
		}
		return null;
	}

	private static boolean isApiDocs(HttpServletRequest request) {
		PathContainer path = ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication();
		return API_DOCS_PATTERNS.stream().anyMatch(pattern -> pattern.matches(path));
	}

	private static final class IsolatedAttributesRequest extends HttpServletRequestWrapper {

		private static final Object REMOVED = new Object();

		private final Map<String, Object> attributes = new HashMap<>();

		IsolatedAttributesRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public Object getAttribute(String name) {
			Object value = this.attributes.get(name);
			if (value == REMOVED) {
				return null;
			}
			return (value != null) ? value : super.getAttribute(name);
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			Set<String> names = new HashSet<>(Collections.list(super.getAttributeNames()));
			this.attributes.forEach((name, value) -> {
				if (value == REMOVED) {
					names.remove(name);
				}
				else {
					names.add(name);
				}
			});
			return Collections.enumeration(names);
		}

		@Override
		public void setAttribute(String name, Object value) {
			this.attributes.put(name, (value != null) ? value : REMOVED);
		}

		@Override
		public void removeAttribute(String name) {
			this.attributes.put(name, REMOVED);
		}

	}

}
