package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.content.activity.ActivityStudentView;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.projection.StudentViewProjector;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Leitura do aluno sobre a versão {@code published} de cada Unidade (história 1.7). Tudo passa pelo
 * {@link StudentViewProjector}: nenhum campo {@code x-fluveny-secret} sai daqui.
 *
 * <p>Estágios liberados: só o primeiro Estágio da Unidade de menor {@code order}. Os demais
 * respondem {@code 401 UNAUTHENTICATED} até existir sessão; a 2.3 troca a regra por "sessão de
 * aluno libera os demais". A regra fica aqui porque a rota é {@code @PublicRoute}.
 */
@Service
public class PublishedUnits {

    private static final int CACHE_SIZE = 64;

    private final UnitVersionStore versions;

    private final UnitPointerStore pointers;

    private final StudentViewProjector projector;

    /** Versões já lidas, por id. São imutáveis, então o cache nunca fica velho. */
    private final Map<String, JsonNode> parsed =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, JsonNode> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    PublishedUnits(
            UnitVersionStore versions, UnitPointerStore pointers, StudentViewProjector projector) {
        this.versions = versions;
        this.pointers = pointers;
        this.projector = projector;
    }

    /** As Unidades publicadas, por {@code order}. */
    public List<StudentViews.UnitListItem> list() {
        return published().stream()
                .map(unit ->
                        new StudentViews.UnitListItem(
                                unit.unitKey(),
                                unit.order(),
                                title(unit.unit()),
                                unit.stages().isEmpty(),
                                unit.versionId()))
                .toList();
    }

    /** O resumo da Unidade publicada; {@code 404} se ela não tem versão publicada. */
    public StudentViews.Unit unit(String unitKey) {
        ContentKeys.key(unitKey);
        Published unit = find(unitKey);
        List<StudentViews.StageSummary> stages = new ArrayList<>();
        for (JsonNode stage : unit.stages()) {
            stages.add(
                    new StudentViews.StageSummary(
                            stage.get("stageKey").asString(),
                            title(stage),
                            stage.get("activities").size()));
        }
        List<StudentViews.UsefulExpression> expressions = new ArrayList<>();
        for (JsonNode expression : unit.unit().path("usefulExpressions")) {
            expressions.add(this.projector.read(expression, StudentViews.UsefulExpression.class));
        }
        return new StudentViews.Unit(
                unit.versionId(),
                unit.unitKey(),
                unit.order(),
                title(unit.unit()),
                stages.isEmpty(),
                stages,
                expressions);
    }

    /**
     * O Estágio com as Atividades na visão do aluno.
     *
     * @throws ApiException {@code 404} se a Unidade ou o Estágio não existem na versão publicada;
     *     {@code 401 UNAUTHENTICATED} se o Estágio não está liberado
     */
    public StudentViews.Stage stage(String unitKey, String stageKey) {
        ContentKeys.key(unitKey);
        ContentKeys.key(stageKey);
        Published unit = find(unitKey);
        JsonNode stage =
                unit.stages().stream()
                        .filter(candidate -> stageKey.equals(candidate.get("stageKey").asString()))
                        .findFirst()
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "STAGE_NOT_FOUND",
                                        "O Estágio não existe na Unidade publicada."));
        if (!isUnlocked(unit, stageKey)) {
            throw new ApiException(
                    ErrorCode.UNAUTHENTICATED,
                    "Entre na sua conta para acessar os próximos Estágios.");
        }
        List<ActivityStudentView> activities = new ArrayList<>();
        for (JsonNode activity : stage.get("activities")) {
            activities.add(this.projector.activity(activity));
        }
        return new StudentViews.Stage(unit.versionId(), stageKey, title(stage), activities);
    }

    /**
     * A Unidade publicada de menor {@code order}: a única com Estágio liberado sem sessão (a mesma
     * regra do {@link #stage}). Vazio se nada foi publicado.
     */
    public Optional<String> firstUnitKey() {
        return published().stream().findFirst().map(Published::unitKey);
    }

    /** Só o primeiro Estágio da Unidade publicada de menor {@code order}. */
    private boolean isUnlocked(Published unit, String stageKey) {
        Optional<Published> first = published().stream().findFirst();
        return first.isPresent()
                && first.get().unitKey().equals(unit.unitKey())
                && !unit.stages().isEmpty()
                && unit.stages().get(0).get("stageKey").asString().equals(stageKey);
    }

    private ContentText title(JsonNode document) {
        return this.projector.read(document.get("title"), ContentText.class);
    }

    private Published find(String unitKey) {
        UnitPointer pointer =
                this.pointers
                        .findById(unitKey)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "UNIT_NOT_FOUND",
                                        "A Unidade não existe ou ainda não foi publicada."));
        UnitVersion version =
                this.versions
                        .findById(pointer.publishedVersionId())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Ponteiro de "
                                                + unitKey
                                                + " para versão inexistente "
                                                + pointer.publishedVersionId()));
        return toPublished(version);
    }

    private List<Published> published() {
        List<UnitPointer> all = this.pointers.findAll();
        Map<String, UnitVersion> byId =
                this.versions
                        .findAllById(all.stream().map(UnitPointer::publishedVersionId).toList())
                        .stream()
                        .collect(Collectors.toMap(UnitVersion::id, Function.identity()));
        return all.stream()
                .map(pointer -> byId.get(pointer.publishedVersionId()))
                .filter(version -> version != null)
                .map(this::toPublished)
                .sorted(Comparator.comparingInt(Published::order).thenComparing(Published::unitKey))
                .toList();
    }

    private Published toPublished(UnitVersion version) {
        JsonNode bundle;
        synchronized (this.parsed) {
            bundle = this.parsed.get(version.id());
        }
        if (bundle == null) {
            JsonNode raw = UnitBundle.MAPPER.readTree(version.canonicalJson());
            // Tudo o que fica no cache já passou pelo projetor, guiado pelo schema.
            ObjectNode projected = UnitBundle.MAPPER.createObjectNode();
            projected.set("unit", this.projector.project(raw.get("unit"), ContentSchemas.UNIT));
            ArrayNode stages = projected.putArray("stages");
            for (JsonNode stage : raw.get("stages")) {
                stages.add(this.projector.project(stage, ContentSchemas.STAGE));
            }
            bundle = projected;
            synchronized (this.parsed) {
                this.parsed.put(version.id(), bundle);
            }
        }
        List<JsonNode> stages = new ArrayList<>();
        bundle.get("stages").forEach(stages::add);
        return new Published(
                version.id(), version.unitKey(), version.order(), bundle.get("unit"), stages);
    }

    private record Published(
            String versionId, String unitKey, int order, JsonNode unit, List<JsonNode> stages) {}
}
