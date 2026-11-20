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
 * No perfil {@code prod} (o padrão, AD-18), a API não sobe sem as variáveis obrigatórias. Roda
 * antes de qualquer bean ser criado, para a falha citar a variável e não um erro de conexão. A
 * chave dos recibos do Visitante ({@code FLUVENY_RECEIPT_KEY}, história 1.12) é obrigatória aqui;
 * fora de {@code prod}, sem ela, {@code learning} gera uma chave na partida. As origens web da CORS
 * ({@code FLUVENY_WEB_ORIGINS}, história 1.22) também: sem elas, o App web não funciona. E o
 * segredo de proxy ({@code FLUVENY_PROXY_SECRET}, história 1.6): sem ele, a API ficaria aberta a
 * quem pula o Worker. E o endereço do App ({@code FLUVENY_APP_URL}, história 2.2): sem ele, o link
 * do e-mail de confirmação não abre. O SMTP ({@code SPRING_MAIL_*}, {@code FLUVENY_MAIL_FROM}) não
 * é obrigatório: sem ele, os e-mails ficam na fila.
 */
@Component
@Profile("prod")
class ProdEnvironmentGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final List<String> REQUIRED_VARIABLES =
            List.of(
                    "SPRING_DATA_MONGODB_URI",
                    "FLUVENY_RECEIPT_KEY",
                    "FLUVENY_WEB_ORIGINS",
                    "FLUVENY_PROXY_SECRET",
                    "FLUVENY_APP_URL");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing =
                REQUIRED_VARIABLES.stream()
                        .filter(name -> !StringUtils.hasText(this.environment.getProperty(name)))
                        .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Perfil prod sem variável de ambiente obrigatória: "
                            + String.join(", ", missing));
        }
    }
}
