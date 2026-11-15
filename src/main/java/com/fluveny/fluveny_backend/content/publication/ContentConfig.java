package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.content.projection.StudentViewProjector;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import com.fluveny.fluveny_backend.content.validation.ContentValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Os schemas do classpath ({@code content/schema/}, copiado pelo build), o validador e o projetor
 * da visão do aluno, um de cada na aplicação.
 */
@Configuration(proxyBeanMethods = false)
class ContentConfig {

    @Bean
    ContentSchemas contentSchemas() {
        return ContentSchemas.loadClasspath();
    }

    @Bean
    ContentValidator contentValidator(ContentSchemas schemas) {
        return new ContentValidator(schemas);
    }

    @Bean
    StudentViewProjector studentViewProjector(ContentSchemas schemas) {
        return new StudentViewProjector(schemas);
    }
}
