package com.fluveny.fluveny_backend.platform.security;

import java.lang.reflect.AnnotatedElement;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * O que um handler declara sobre quem pode chamá-lo (AD-12). A declaração do
 * método vence a da classe. Sem declaração, ou com {@code @PublicRoute} e
 * {@code @RequiresRole} juntos no mesmo elemento, a rota é negada.
 *
 * @param kind o tipo de declaração
 * @param roles os papéis aceitos, quando {@code kind} é {@link Kind#ROLES}
 */
public record RouteDeclaration(Kind kind, List<Role> roles) {

	public enum Kind {

		PUBLIC, ROLES, UNDECLARED, CONFLICTING

	}

	public static RouteDeclaration of(HandlerMethod handler) {
		RouteDeclaration onMethod = on(handler.getMethod());
		return onMethod.kind() == Kind.UNDECLARED ? on(handler.getBeanType()) : onMethod;
	}

	/** Se a rota pode ser servida a alguém: pública ou com papel válido. */
	public boolean isDeclared() {
		return this.kind == Kind.PUBLIC || this.kind == Kind.ROLES;
	}

	private static RouteDeclaration on(AnnotatedElement element) {
		boolean isPublic = AnnotatedElementUtils.hasAnnotation(element, PublicRoute.class);
		RequiresRole requires = AnnotatedElementUtils.findMergedAnnotation(element, RequiresRole.class);
		if (isPublic && requires != null) {
			return new RouteDeclaration(Kind.CONFLICTING, List.of());
		}
		if (isPublic) {
			return new RouteDeclaration(Kind.PUBLIC, List.of());
		}
		if (requires != null) {
			return requires.value().length == 0 ? new RouteDeclaration(Kind.CONFLICTING, List.of())
					: new RouteDeclaration(Kind.ROLES, List.of(requires.value()));
		}
		return new RouteDeclaration(Kind.UNDECLARED, List.of());
	}

}
