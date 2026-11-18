package com.fluveny.fluveny_backend.content;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.publication.ContentKeys;
import com.fluveny.fluveny_backend.content.publication.PublishedUnits;
import com.fluveny.fluveny_backend.content.publication.UnitVersion;
import com.fluveny.fluveny_backend.content.publication.UnitVersionStore;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Atividade completa, com os campos {@code x-fluveny-secret}, de qualquer Versão de Unidade
 * (AD-6, AD-7): a porta do corretor de {@code learning} para o conteúdo. A versão pode não ser mais
 * a publicada, porque a Tentativa é corrigida contra a versão que o aluno viu.
 *
 * <p>Nada daqui vai para rota do aluno: quem chama devolve só o Resultado do corretor. As versões
 * lidas ficam num cache próprio, separado do cache projetado do {@link PublishedUnits}.
 */
@Service
public class ContentActivities {

    private static final int CACHE_SIZE = 64;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final UnitVersionStore versions;

    private final PublishedUnits published;

    /** Atividades por versão. Versões são imutáveis, então o cache nunca fica velho. */
    private final Map<String, Map<String, Located>> parsed =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, Map<String, Located>> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    ContentActivities(UnitVersionStore versions, PublishedUnits published) {
        this.versions = versions;
        this.published = published;
    }

    /**
     * Uma Atividade numa Versão de Unidade.
     *
     * @param activity a Atividade completa, com gabarito
     * @param openToVisitor se a Atividade está no Estágio de posição 0 da versão e a Unidade é a
     *     publicada de menor {@code order}: a mesma regra do {@code getStage} sem sessão
     */
    public record Found(
            String versionId,
            String unitKey,
            String stageKey,
            Activity activity,
            boolean openToVisitor) {}

    /**
     * @throws ApiException {@code 400 INVALID_ID} se {@code versionId} ou {@code activityKey} estão
     *     fora do formato; {@code 404 NOT_FOUND} se a versão ou a Atividade não existem
     */
    public Found find(String versionId, String activityKey) {
        ContentKeys.versionId(versionId);
        ContentKeys.key(activityKey);
        Map<String, Located> activities = activities(versionId);
        Located located = activities.get(activityKey);
        if (located == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "A Atividade não existe nesta versão.");
        }
        boolean open =
                located.stageIndex() == 0
                        && this.published
                                .firstUnitKey()
                                .map(located.unitKey()::equals)
                                .orElse(false);
        return new Found(
                versionId, located.unitKey(), located.stageKey(), located.activity(), open);
    }

    /**
     * As {@code activityKey}s de um Estágio numa Versão de Unidade: a base da cobertura do Estágio
     * (AD-14, história 1.20).
     *
     * @return vazio se o Estágio não está na versão
     * @throws ApiException {@code 400 INVALID_ID} se {@code versionId} está fora do formato; {@code
     *     404 NOT_FOUND} se a versão não existe
     */
    public Set<String> stageActivityKeys(String versionId, String stageKey) {
        ContentKeys.versionId(versionId);
        return activities(versionId).values().stream()
                .filter(located -> located.stageKey().equals(stageKey))
                .map(located -> located.activity().activityKey())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, Located> activities(String versionId) {
        synchronized (this.parsed) {
            Map<String, Located> cached = this.parsed.get(versionId);
            if (cached != null) {
                return cached;
            }
        }
        UnitVersion version =
                this.versions
                        .findById(versionId)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND, "A versão da Unidade não existe."));
        JsonNode bundle = MAPPER.readTree(version.canonicalJson());
        Map<String, Located> activities = new LinkedHashMap<>();
        JsonNode stages = bundle.get("stages");
        for (int i = 0; i < stages.size(); i++) {
            JsonNode stage = stages.get(i);
            String stageKey = stage.get("stageKey").asString();
            for (JsonNode node : stage.get("activities")) {
                Activity activity = MAPPER.treeToValue(node, Activity.class);
                activities.put(
                        activity.activityKey(),
                        new Located(version.unitKey(), i, stageKey, activity));
            }
        }
        Map<String, Located> result = Map.copyOf(activities);
        synchronized (this.parsed) {
            this.parsed.put(versionId, result);
        }
        return result;
    }

    private record Located(String unitKey, int stageIndex, String stageKey, Activity activity) {}
}
