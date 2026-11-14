package com.fluveny.fluveny_backend.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * Fonte única da hierarquia de papéis (AD-12): {@code ADMIN > CONTENT_CREATOR}.
 * {@code STUDENT} fica fora: a equipe não herda o papel do Aluno, e o Aluno não
 * herda nenhum outro.
 */
@Configuration(proxyBeanMethods = false)
public class RoleHierarchyConfig {

	@Bean
	public static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
			.role(Role.ADMIN.name())
			.implies(Role.CONTENT_CREATOR.name())
			.build();
	}

}
