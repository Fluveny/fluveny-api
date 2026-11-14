package com.fluveny.fluveny_backend.platform.config;

import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * No perfil {@code prod} (o padrão, AD-18), a API não sobe sem as variáveis
 * obrigatórias. Roda antes de qualquer bean ser criado, para a falha citar a
 * variável e não um erro de conexão.
 */
@Component
@Profile("prod")
class ProdEnvironmentGuard implements BeanFactoryPostProcessor, EnvironmentAware {

	static final List<String> REQUIRED_VARIABLES = List.of("SPRING_DATA_MONGODB_URI");

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		List<String> missing = REQUIRED_VARIABLES.stream()
			.filter(name -> !StringUtils.hasText(this.environment.getProperty(name)))
			.toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException(
					"Perfil prod sem variável de ambiente obrigatória: " + String.join(", ", missing));
		}
	}

}
