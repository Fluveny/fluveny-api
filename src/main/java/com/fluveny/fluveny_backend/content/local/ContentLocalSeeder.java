package com.fluveny.fluveny_backend.content.local;

import com.fluveny.fluveny_backend.content.infrastructure.ContentIndexes;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.content.validation.ContentError;
import com.fluveny.fluveny_backend.content.validation.ContentValidator;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code content} (AD-18, Decisão 5 da história 1.7): publica cada pasta de
 * {@code content/units/} com {@code publish=true}, pelo mesmo {@link PublishUnitVersion} da rota
 * admin. Pula só a compatibilidade de Tipo com o {@code minAppVersion}, com um aviso no log; as
 * outras validações valem. Unidade inválida fica de fora, com os erros no log. Idempotente: o id é
 * o hash, e republicar o mesmo conteúdo não cria nada.
 */
@Component
@Profile("local")
@Order(100)
class ContentLocalSeeder implements LocalDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(ContentLocalSeeder.class);

    private final PublishUnitVersion publisher;

    private final ContentValidator validator;

    private final ContentIndexes indexes;

    private final Path contentDir;

    ContentLocalSeeder(
            PublishUnitVersion publisher,
            ContentValidator validator,
            ContentIndexes indexes,
            @Value("${fluveny.content.dir:content}") String contentDir) {
        this.publisher = publisher;
        this.validator = validator;
        this.indexes = indexes;
        this.contentDir = Path.of(contentDir);
    }

    @Override
    public String name() {
        return "content";
    }

    @Override
    public void seed() {
        // O reset apaga o banco com os índices; recria antes de gravar.
        this.indexes.ensure();
        Path units = this.contentDir.resolve("units");
        Path media = this.contentDir.resolve("media.json");
        if (!Files.isDirectory(units)) {
            log.warn(
                    "Sem {}: nenhuma Unidade publicada (FLUVENY_CONTENT_DIR aponta a pasta content).",
                    units.toAbsolutePath());
            return;
        }
        log.warn(
                "Perfil local: a compatibilidade de Tipo com o minAppVersion não é conferida na"
                        + " publicação das Unidades de {}.",
                units.toAbsolutePath());
        for (Path unitDir : unitDirs(units)) {
            String unitKey = unitDir.getFileName().toString();
            List<ContentError> errors = this.validator.validateUnit(unitDir, media);
            if (!errors.isEmpty()) {
                log.error(
                        "Unidade {} não publicada: {} erro(s) de conteúdo.\n  {}",
                        unitKey,
                        errors.size(),
                        String.join("\n  ", errors.stream().map(ContentError::format).toList()));
                continue;
            }
            try {
                PublishUnitVersion.Result result =
                        this.publisher.publish(
                                unitKey,
                                UnitBundle.fromDirectory(unitDir, media),
                                true,
                                TypeCheck.SKIP);
                log.info(
                        "Unidade {} publicada: {} ({}).",
                        unitKey,
                        result.versionId(),
                        result.created() ? "versão nova" : "versão já existia");
            } catch (ApiException ex) {
                log.error("Unidade {} não publicada: {} {}", unitKey, ex.getCode(), ex.getDetail());
            } catch (RuntimeException ex) {
                // Mongo, JSON ou disco: a Unidade fica de fora, e as outras e os próximos
                // carregadores seguem.
                log.error("Unidade {} não publicada: erro inesperado.", unitKey, ex);
            }
        }
    }

    private static List<Path> unitDirs(Path units) {
        try (Stream<Path> entries = Files.list(units)) {
            return entries.filter(Files::isDirectory)
                    .filter(entry -> !entry.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
