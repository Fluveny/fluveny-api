package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.content.validation.ContentError;
import com.fluveny.fluveny_backend.content.validation.ContentValidator;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.MetaProperties;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Publica uma Versão de Unidade (AD-4, história 1.7): valida o pacote com as regras da CI (schema e
 * FR-29), confere que o {@code minAppVersion} renderiza cada Tipo usado (AD-11), calcula o id pelo
 * JSON canônico, grava a {@link UnitVersion} se ela ainda não existe e, com {@code publish}, move o
 * ponteiro {@code published}. Nada é gravado se alguma regra falhar.
 */
@Service
public class PublishUnitVersion {

    /** Se a compatibilidade de Tipo com o {@code minAppVersion} é conferida. */
    public enum TypeCheck {
        /** Toda publicação pela API. */
        ENFORCE,
        /** Só o carregador do perfil {@code local} (Decisão 5 da 1.7). */
        SKIP
    }

    /**
     * O resultado da publicação.
     *
     * @param versionId o id da versão
     * @param created se a versão foi gravada agora ({@code false}: já existia, nada novo)
     * @param published se o ponteiro aponta para ela depois desta chamada
     */
    public record Result(String versionId, boolean created, boolean published) {}

    private static final Logger log = LoggerFactory.getLogger(PublishUnitVersion.class);

    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private final ContentValidator validator;

    private final UnitVersionStore versions;

    private final UnitPointerStore pointers;

    private final ObjectProvider<ActivityTypeSource> typeSources;

    private final MetaProperties meta;

    PublishUnitVersion(
            ContentValidator validator,
            UnitVersionStore versions,
            UnitPointerStore pointers,
            ObjectProvider<ActivityTypeSource> typeSources,
            MetaProperties meta) {
        this.validator = validator;
        this.versions = versions;
        this.pointers = pointers;
        this.typeSources = typeSources;
        this.meta = meta;
    }

    /**
     * @throws ApiException {@code 400 INVALID_ID} (chave fora do formato), {@code 422
     *     UNIT_KEY_MISMATCH}, {@code 422 INVALID_CONTENT} ou {@code 422 UNSUPPORTED_ACTIVITY_TYPE},
     *     os dois últimos com {@code errors}
     */
    public Result publish(String unitKey, JsonNode bundle, boolean publish, TypeCheck typeCheck) {
        ContentKeys.key(unitKey);
        JsonNode declared = (bundle == null) ? null : bundle.path("unit").path("unitKey");
        if (declared != null && declared.isString() && !declared.asString().equals(unitKey)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "UNIT_KEY_MISMATCH",
                    "O unitKey do pacote ("
                            + declared.asString()
                            + ") difere do unitKey do caminho ("
                            + unitKey
                            + ").");
        }
        List<ContentError> errors = this.validator.validateBundle(unitKey, bundle);
        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_CONTENT",
                    "O conteúdo tem " + errors.size() + " erro(s); nada foi gravado.",
                    Map.of("errors", errors.stream().map(ContentProblem::of).toList()));
        }
        if (typeCheck == TypeCheck.ENFORCE) {
            checkTypes(unitKey, bundle);
        }

        String canonical = CanonicalJson.of(bundle);
        String id = CanonicalJson.sha256Id(canonical);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        JsonNode unit = bundle.get("unit");
        boolean created =
                this.versions.insertIfAbsent(
                        new UnitVersion(
                                id,
                                unitKey,
                                unit.get("schemaVersion").asInt(),
                                unit.get("order").asInt(),
                                canonical,
                                now));
        String previous =
                this.pointers.findById(unitKey).map(UnitPointer::publishedVersionId).orElse(null);
        if (publish) {
            this.pointers.move(unitKey, id, now);
        }
        boolean published = publish || id.equals(previous);
        log.info(
                "Publicação de {}: versionId={} created={} published={} publishedVersionId"
                        + " anterior={}",
                unitKey,
                id,
                created,
                published,
                previous);
        return new Result(id, created, published);
    }

    /** A versão, se existe e é da Unidade. */
    public Optional<VersionInfo> version(String unitKey, String versionId) {
        ContentKeys.key(unitKey);
        ContentKeys.versionId(versionId);
        return this.versions
                .findById(versionId)
                .filter(version -> version.unitKey().equals(unitKey))
                .map(version ->
                        new VersionInfo(
                                version.id(),
                                version.unitKey(),
                                version.schemaVersion(),
                                version.order(),
                                version.createdAt(),
                                this.pointers
                                        .findById(unitKey)
                                        .map(pointer ->
                                                version.id().equals(pointer.publishedVersionId()))
                                        .orElse(false)));
    }

    /** Metadados de uma versão, sem o conteúdo. */
    public record VersionInfo(
            String versionId,
            String unitKey,
            int schemaVersion,
            int order,
            Instant createdAt,
            boolean published) {}

    /** Cada Tipo usado precisa de {@code sinceAppVersion} não nulo e ≤ {@code minAppVersion}. */
    private void checkTypes(String unitKey, JsonNode bundle) {
        Map<String, String> since = new HashMap<>();
        this.typeSources
                .orderedStream()
                .flatMap(source -> source.activityTypes().stream())
                .forEach(support -> {
                    // O primeiro source vence, inclusive com sinceAppVersion nulo (putIfAbsent
                    // deixaria um source posterior trocar o nulo).
                    if (!since.containsKey(support.type())) {
                        since.put(support.type(), support.sinceAppVersion());
                    }
                });
        String min = this.meta.minVersion();
        List<ContentProblem> problems = new ArrayList<>();
        JsonNode stages = bundle.get("stages");
        for (JsonNode stage : stages) {
            String file =
                    "units/" + unitKey + "/stages/" + stage.get("stageKey").asString() + ".json";
            JsonNode activities = stage.get("activities");
            for (int i = 0; i < activities.size(); i++) {
                JsonNode activity = activities.get(i);
                String type = activity.get("type").asString();
                String version = since.get(type);
                if (version == null || !atMost(version, min)) {
                    problems.add(
                            new ContentProblem(
                                    file,
                                    "/activities/" + i + "/type",
                                    activity.path("activityKey").asString(null),
                                    "UNSUPPORTED_ACTIVITY_TYPE",
                                    (version == null)
                                            ? "O Tipo "
                                                    + type
                                                    + " ainda não tem sinceAppVersion: nenhuma"
                                                    + " versão do App o renderiza."
                                            : "O Tipo "
                                                    + type
                                                    + " só é renderizado a partir do App "
                                                    + version
                                                    + ", acima do mínimo "
                                                    + min
                                                    + "."));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "UNSUPPORTED_ACTIVITY_TYPE",
                    "O conteúdo usa Tipo de Atividade que o App mínimo ("
                            + min
                            + ") não renderiza; nada foi gravado.",
                    Map.of("errors", List.copyOf(problems)));
        }
    }

    /** {@code a ≤ b} em X.Y.Z; fora do formato, {@code false}. */
    static boolean atMost(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        if (left == null || right == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) {
                return left[i] < right[i];
            }
        }
        return true;
    }

    private static int[] parse(String version) {
        Matcher matcher = SEMVER.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
