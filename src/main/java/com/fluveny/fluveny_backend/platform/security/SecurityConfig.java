package com.fluveny.fluveny_backend.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia de segurança única da API (AD-12): stateless, sem basic auth, sem formulário e sem a senha
 * gerada do Boot; deny-by-default pelo {@link RouteAuthorizationManager}. A sessão (Bearer e
 * cookie) chega nas histórias 2.3 e 4.2; até lá nenhuma rota autentica.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    /**
     * Únicas exceções por caminho: a documentação pública da API, em todos os ambientes (Decisão 2
     * da história 1.2).
     */
    static final String[] API_DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RouteAuthorizationManager routeAuthorization,
            ProblemSecurityHandlers problemHandlers)
            throws Exception {
        return http
                // Sem cookie de sessão ainda, não há o que proteger com CSRF (o Painel chega na
                // 4.2).
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(problemHandlers)
                                .accessDeniedHandler(problemHandlers))
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(API_DOCS_PATHS)
                                .permitAll()
                                .anyRequest()
                                .access(routeAuthorization))
                .build();
    }
}
