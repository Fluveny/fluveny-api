package com.fluveny.fluveny_backend.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Autowired
    SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Auth Controller
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout-header").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/captcha/check").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/username").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").permitAll()

                        // Grammar Rule Controller
                        .requestMatchers(HttpMethod.GET, "/api/v1/grammar-rules").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/grammar-rules/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/grammar-rules/search").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/grammar-rules").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/grammar-rules/{id}").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/grammar-rules/{id}").hasRole("CONTENT_CREATOR")

                        // Grammar Exercise Controller
                        .requestMatchers(HttpMethod.POST, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/exercises").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/exercises/{id}").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/exercises/{id}").permitAll()

                        // Grammar Rule Presentation Controller
                        .requestMatchers(HttpMethod.POST, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/presentation").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/presentation{id}").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/presentation{id}").permitAll()

                        // Level Controller
                        .requestMatchers(HttpMethod.GET, "/api/v1/levels").permitAll()

                        // User controller
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()

                        // Role controller
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles").permitAll()

                        // Module Controller
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/modules").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/modules/{id}").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/modules/{id}").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id}/grammar-rule-modules/{GrammarRuleModuleId}/contents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id}/grammar-rule-modules").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/search-student").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/modules/{id}/grammar-rule-modules/{grammarRuleModuleId}").hasRole("CONTENT_CREATOR")

                        // Introduction Controller
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules/{id}/introduction").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/modules/{id}/introduction").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/modules/{id}/introduction").hasRole("CONTENT_CREATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/modules/{id}/introduction").hasRole("CONTENT_CREATOR")


                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
