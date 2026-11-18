package com.fluveny.fluveny_backend.content.validation;

import com.fluveny.fluveny_backend.content.activity.CardsActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.ExpressionActivity;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.GrammarActivity;
import com.fluveny.fluveny_backend.content.activity.MatchActivity;
import com.fluveny.fluveny_backend.content.activity.SpeechActivity;
import com.networknt.schema.Error;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valida o Conteúdo Estruturado (FR-29, AD-4, AD-5): o JSON Schema de {@code content/schema/} e as
 * regras que ele não expressa (resposta aceita, mídia no manifesto, variantes de pronome,
 * formatação, chaves únicas e arquivos de Estágio). Devolve todos os erros de uma vez, cada um com
 * arquivo, ponteiro JSON, chave e regra. Roda nos testes, portanto na CI ({@code
 * ContentUnitsTest}).
 */
public final class ContentValidator {

    static final String UNIT_FILE = "unit.json";

    static final String STAGES_DIR = "stages";

    static final String MEDIA_FILE = "media.json";

    static final String UNITS_DIR = "units";

    private static final String JSON = ".json";

    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    /**
     * Chaves únicas dentro da própria lista (a da opção, a das Expressões Úteis, a da fala, a do
     * bloco de cartões, a da resposta do Associar, a da palavra do banco das Lacunas e as das três
     * abas do Grammar Time). O {@code itemKey}, inclusive o do cartão, já é único na Unidade
     * inteira.
     */
    private static final Set<String> LIST_SCOPED_KEYS =
            Set.of(
                    "optionKey",
                    "expressionKey",
                    "lineKey",
                    "blockKey",
                    "responseKey",
                    "wordKey",
                    "sentenceKey",
                    "ruleKey",
                    "exampleKey");

    /** Chaves únicas na Unidade inteira (AD-5), cada uma no próprio espaço. */
    private static final Set<String> UNIT_SCOPED_KEYS = Set.of("activityKey", "itemKey");

    /**
     * A regra de resposta aceita de cada Tipo (FR-29). Todo {@code type} da união do schema precisa
     * de uma; o construtor falha se faltar.
     */
    private static final Map<String, ActivityRule> ACCEPTED_ANSWER_RULES =
            Map.of(
                    ChoiceActivity.TYPE,
                    ContentValidator::checkChoiceAnswer,
                    SpeechActivity.TYPE,
                    ContentValidator::checkSpeechAnswer,
                    MatchActivity.TYPE,
                    ContentValidator::checkMatchAnswer,
                    GapsActivity.TYPE,
                    ContentValidator::checkGapsAnswer,
                    FreeActivity.TYPE,
                    ContentValidator::checkFreeAnswer);

    /**
     * Os Tipos expositivos (histórias 1.13, 1.14 e 1.19): sem Answer, sem regra de resposta aceita
     * e sem Tentativa; a conclusão é por {@code completions}. Ficam fora da exigência de {@link
     * #ACCEPTED_ANSWER_RULES}, e o {@code ActivityFidelityTest} dispensa o Answer deles.
     */
    public static final Set<String> EXPOSITORY_TYPES =
            Set.of(
                    DialogueActivity.TYPE,
                    CardsActivity.TYPE,
                    ExpressionActivity.TYPE,
                    GrammarActivity.TYPE);

    /** Regras de um Tipo que o JSON Schema não expressa, além da resposta aceita. */
    private static final Map<String, ActivityRule> TYPE_RULES =
            Map.of(
                    DialogueActivity.TYPE,
                    ContentValidator::checkDialogueAsks,
                    ExpressionActivity.TYPE,
                    ContentValidator::checkExpressionTerm,
                    SpeechActivity.TYPE,
                    ContentValidator::checkSpeechWords,
                    GapsActivity.TYPE,
                    ContentValidator::checkGapsShape,
                    FreeActivity.TYPE,
                    ContentValidator::checkFreeShape,
                    GrammarActivity.TYPE,
                    ContentValidator::checkGrammarHighlight);

    /**
     * Um termo {@code [[texto|expressionKey]]}. Marcação malformada (texto ou chave em branco) e
     * chave fora das {@code usefulExpressions} são do {@link MarkupChecker} ({@code
     * INVALID_MARKUP}).
     */
    private static final Pattern TERM = Pattern.compile("\\[\\[([^\\[\\]|]*)\\|([^\\[\\]|]*)]]");

    /** Um placeholder, com o mesmo padrão do {@code ContentRenderer}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}\\s]+)}");

    /** Uma lacuna das Lacunas: três ou mais {@code _}. */
    private static final Pattern GAP = Pattern.compile("_{3,}");

    /** As variantes de pronome, na ordem do texto. */
    private static final List<String> PRONOUNS = List.of("he", "she", "they");

    /** Uma letra ou um número: o que faz uma palavra contar no critério da Fala. */
    private static final Pattern WORD_CHAR = Pattern.compile("[\\p{L}\\p{N}]");

    private final ContentSchemas schemas;

    private final SchemaWalker walker;

    private final MarkupChecker markup;

    private final Set<String> activityTypes;

    public ContentValidator(ContentSchemas schemas) {
        this.schemas = schemas;
        this.walker = new SchemaWalker(schemas);
        this.markup = new MarkupChecker(schemas.placeholderGrammar());
        this.activityTypes = schemas.activityTypes();
        Set<String> missing = new HashSet<>(this.activityTypes);
        missing.removeAll(ACCEPTED_ANSWER_RULES.keySet());
        missing.removeAll(EXPOSITORY_TYPES);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Tipo sem regra de resposta aceita no ContentValidator: " + missing);
        }
        Set<String> both = new HashSet<>(EXPOSITORY_TYPES);
        both.retainAll(ACCEPTED_ANSWER_RULES.keySet());
        if (!both.isEmpty()) {
            throw new IllegalStateException(
                    "Tipo expositivo com regra de resposta aceita no ContentValidator: " + both);
        }
    }

    /** Validador com os schemas de {@code schemaDir} ({@code content/schema/}). */
    public static ContentValidator forSchemaDir(Path schemaDir) {
        return new ContentValidator(ContentSchemas.load(schemaDir));
    }

    /** Os Tipos que têm regra de resposta aceita. */
    static Set<String> acceptedAnswerTypes() {
        return ACCEPTED_ANSWER_RULES.keySet();
    }

    /**
     * Valida uma pasta de conteúdo inteira: {@code media.json} e cada {@code units/<unitKey>/}. Os
     * arquivos do relatório são relativos a {@code contentRoot}.
     */
    public List<ContentError> validate(Path contentRoot) {
        Report report = new Report(contentRoot);
        Set<String> mediaKeys = readManifest(contentRoot.resolve(MEDIA_FILE), report);
        Path units = contentRoot.resolve(UNITS_DIR);
        Map<Long, List<Located>> orders = new TreeMap<>();
        if (Files.isDirectory(units)) {
            for (Path entry : list(units)) {
                if (!Files.isDirectory(entry)) {
                    report.add(
                            entry,
                            "",
                            null,
                            ContentRule.UNEXPECTED_FILE,
                            "Arquivo solto em "
                                    + UNITS_DIR
                                    + "/: cada Unidade é uma pasta "
                                    + UNITS_DIR
                                    + "/<unitKey>/.");
                    continue;
                }
                JsonNode unit = checkUnitDir(entry, new Scope(mediaKeys), report);
                if (unit != null && unit.path("order").isIntegralNumber()) {
                    orders.computeIfAbsent(unit.get("order").asLong(), order -> new ArrayList<>())
                            .add(
                                    new Located(
                                            report.name(entry.resolve(UNIT_FILE)),
                                            "/order",
                                            unit.path("unitKey").asString(null)));
                }
            }
        }
        orders.forEach((order, places) -> {
            if (places.size() > 1) {
                for (Located place : places) {
                    report.add(
                            place.file(),
                            place.pointer(),
                            place.name(),
                            ContentRule.DUPLICATE_KEY,
                            "O order "
                                    + order
                                    + " se repete em "
                                    + places.size()
                                    + " Unidades; ele precisa ser único.");
                }
            }
        });
        return report.errors();
    }

    /**
     * Valida uma pasta de Unidade contra o manifesto de mídia. Os arquivos do relatório são
     * relativos à pasta pai da Unidade.
     */
    public List<ContentError> validateUnit(Path unitDir, Path mediaManifest) {
        Report report = new Report(unitDir.toAbsolutePath().getParent());
        Set<String> mediaKeys = readManifest(mediaManifest, report);
        checkUnitDir(unitDir, new Scope(mediaKeys), report);
        return report.errors();
    }

    /**
     * Valida o pacote de publicação {@code {unit, stages[], media{}}} em memória (história 1.7),
     * com as mesmas regras da pasta. Os arquivos do relatório são os do repositório, relativos a
     * {@code content/}: {@code units/<unitKey>/unit.json}, {@code
     * units/<unitKey>/stages/<stageKey>.json} e {@code media.json}. Além das regras da pasta, o
     * pacote precisa trazer os Estágios na ordem de {@code unit.stages} e, em {@code media}, só as
     * entradas que a Unidade referencia.
     *
     * @param unitKey a Unidade do caminho da publicação; nomeia a pasta dos arquivos
     */
    public List<ContentError> validateBundle(String unitKey, JsonNode bundle) {
        Report report = new Report(Path.of(""));
        String unitDir = UNITS_DIR + "/" + unitKey + "/";
        String unitFile = unitDir + UNIT_FILE;
        if (bundle == null || !bundle.isObject()) {
            report.add(
                    "",
                    "",
                    null,
                    ContentRule.INVALID_BUNDLE,
                    "O pacote precisa ser um objeto {unit, stages, media}.");
            return report.errors();
        }
        for (String name : bundle.propertyNames()) {
            if (!BUNDLE_FIELDS.contains(name)) {
                report.add(
                        "",
                        "/" + JsonPointers.escape(name),
                        null,
                        ContentRule.INVALID_BUNDLE,
                        "Campo fora do pacote: só valem unit, stages e media.");
            }
        }

        Set<String> mediaKeys = new HashSet<>();
        JsonNode media = bundle.get("media");
        if (media == null) {
            report.add(
                    MEDIA_FILE, "", null, ContentRule.MISSING_FILE, "O pacote não traz o media.");
        } else {
            checkSchema(media, ContentSchemas.MEDIA, MEDIA_FILE, report);
            if (media.isObject()) {
                mediaKeys.addAll(media.propertyNames());
            }
        }

        JsonNode unit = bundle.get("unit");
        if (unit == null) {
            report.add(unitFile, "", null, ContentRule.MISSING_FILE, "O pacote não traz o unit.");
        }

        Map<String, StageDoc> stageDocs = new LinkedHashMap<>();
        List<String> bundleOrder = new ArrayList<>();
        JsonNode stages = bundle.get("stages");
        if (stages == null || !stages.isArray()) {
            report.add(
                    "",
                    "/stages",
                    null,
                    ContentRule.INVALID_BUNDLE,
                    "O pacote precisa de stages: a lista dos Estágios, na ordem de unit.stages.");
        } else {
            for (int i = 0; i < stages.size(); i++) {
                JsonNode stage = stages.get(i);
                String key = stage.path("stageKey").asString(null);
                String file =
                        unitDir + STAGES_DIR + "/" + ((key != null) ? key + JSON : "[" + i + "]");
                if (key == null) {
                    // Sem stageKey: o schema aponta a falta, e o Estágio não casa com a lista.
                    key = "[" + i + "]";
                }
                if (stageDocs.containsKey(key)) {
                    report.add(
                            file,
                            "/stageKey",
                            key,
                            ContentRule.INVALID_BUNDLE,
                            "O Estágio " + key + " aparece mais de uma vez em stages do pacote.");
                    continue;
                }
                stageDocs.put(key, new StageDoc(file, key, stage));
                bundleOrder.add(key);
            }
        }

        Scope scope = new Scope(mediaKeys);
        checkUnit(unitKey, unitFile, unit, stageDocs, scope, report);

        if (unit != null) {
            List<String> listed = new ArrayList<>();
            for (JsonNode stageKey : unit.path("stages")) {
                if (stageKey.isString() && !listed.contains(stageKey.asString())) {
                    listed.add(stageKey.asString());
                }
            }
            List<String> present = bundleOrder.stream().filter(listed::contains).toList();
            List<String> expected = listed.stream().filter(stageDocs::containsKey).toList();
            if (!present.equals(expected)) {
                report.add(
                        "",
                        "/stages",
                        null,
                        ContentRule.INVALID_BUNDLE,
                        "Os Estágios do pacote precisam vir na ordem de unit.stages: "
                                + String.join(", ", expected)
                                + ".");
            }
        }
        if (media != null && media.isObject()) {
            for (String key : new TreeSet<>(media.propertyNames())) {
                if (!scope.referencedMedia.contains(key)) {
                    report.add(
                            MEDIA_FILE,
                            "/" + JsonPointers.escape(key),
                            key,
                            ContentRule.UNREFERENCED_MEDIA,
                            "O mediaKey "
                                    + key
                                    + " não é usado pela Unidade; o pacote só traz a mídia que ela"
                                    + " referencia.");
                }
            }
        }
        return report.errors();
    }

    private static final Set<String> BUNDLE_FIELDS = Set.of("unit", "stages", "media");

    private Set<String> readManifest(Path manifest, Report report) {
        JsonNode node = read(manifest, report);
        if (node == null) {
            return Set.of();
        }
        checkSchema(node, ContentSchemas.MEDIA, report.name(manifest), report);
        Set<String> keys = new HashSet<>();
        if (node.isObject()) {
            keys.addAll(node.propertyNames());
        }
        return keys;
    }

    /** Lê a pasta da Unidade e aplica as regras. */
    private JsonNode checkUnitDir(Path unitDir, Scope scope, Report report) {
        Path unitFile = unitDir.resolve(UNIT_FILE);
        JsonNode unit = read(unitFile, report);

        Map<String, StageDoc> stageDocs = new TreeMap<>();
        Path stagesDir = unitDir.resolve(STAGES_DIR);
        if (Files.isDirectory(stagesDir)) {
            for (Path file : list(stagesDir)) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.endsWith(JSON)) {
                    String fileKey = name.substring(0, name.length() - JSON.length());
                    stageDocs.put(
                            fileKey, new StageDoc(report.name(file), fileKey, read(file, report)));
                } else {
                    report.add(
                            file,
                            "",
                            null,
                            ContentRule.UNEXPECTED_FILE,
                            "Em "
                                    + STAGES_DIR
                                    + "/ só ficam os arquivos <stageKey>"
                                    + JSON
                                    + " (extensão em minúsculas, sem subpastas).");
                }
            }
        }
        return checkUnit(
                unitDir.getFileName().toString(),
                report.name(unitFile),
                unit,
                stageDocs,
                scope,
                report);
    }

    /**
     * As regras da Unidade sobre documentos já lidos. {@code unit} é {@code null} quando não deu
     * para ler (o erro já está no relatório); um Estágio com {@code node} nulo idem.
     *
     * @param dirName o nome da pasta da Unidade, que tem de ser o {@code unitKey}
     * @param stageDocs os Estágios por nome de arquivo (sem {@code .json})
     * @return o {@code unit.json}, ou {@code null}
     */
    private JsonNode checkUnit(
            String dirName,
            String unitFile,
            JsonNode unit,
            Map<String, StageDoc> stageDocs,
            Scope scope,
            Report report) {
        List<StageDoc> stageOrder = new ArrayList<>();

        if (unit != null) {
            scope.expressionKeys = new HashSet<>();
            checkSchema(unit, ContentSchemas.UNIT, unitFile, report);
            if (unit.path("unitKey").isString()
                    && !unit.get("unitKey").asString().equals(dirName)) {
                report.add(
                        unitFile,
                        "/unitKey",
                        unit.get("unitKey").asString(),
                        ContentRule.FILE_NAME_MISMATCH,
                        "O unitKey "
                                + unit.get("unitKey").asString()
                                + " difere do nome da pasta "
                                + dirName
                                + ".");
            }
            for (JsonNode expression : unit.path("usefulExpressions")) {
                if (expression.path("expressionKey").isString()) {
                    scope.expressionKeys.add(expression.get("expressionKey").asString());
                }
            }

            Map<String, List<Integer>> listed = new LinkedHashMap<>();
            JsonNode stages = unit.path("stages");
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i).isString()) {
                    listed.computeIfAbsent(stages.get(i).asString(), key -> new ArrayList<>())
                            .add(i);
                }
            }
            listed.forEach((stageKey, positions) -> {
                if (positions.size() > 1) {
                    for (int position : positions) {
                        report.add(
                                unitFile,
                                "/stages/" + position,
                                stageKey,
                                ContentRule.DUPLICATE_KEY,
                                "O stageKey "
                                        + stageKey
                                        + " aparece "
                                        + positions.size()
                                        + " vezes em stages.");
                    }
                }
                StageDoc doc = stageDocs.get(stageKey);
                if (doc == null) {
                    report.add(
                            unitFile,
                            "/stages/" + positions.get(0),
                            stageKey,
                            ContentRule.STAGE_FILE_MISMATCH,
                            "O Estágio "
                                    + stageKey
                                    + " está em stages, mas falta o arquivo "
                                    + STAGES_DIR
                                    + "/"
                                    + stageKey
                                    + JSON
                                    + ".");
                } else {
                    stageOrder.add(doc);
                }
            });
            stageDocs.forEach((stageKey, doc) -> {
                if (!listed.containsKey(stageKey)) {
                    report.add(
                            doc.file(),
                            "",
                            stageKey,
                            ContentRule.STAGE_FILE_MISMATCH,
                            "O arquivo do Estágio "
                                    + stageKey
                                    + " não está em stages do "
                                    + UNIT_FILE
                                    + ".");
                    stageOrder.add(doc);
                }
            });
            checkDocument(unit, ContentSchemas.UNIT, unitFile, scope, report);
        } else {
            stageOrder.addAll(stageDocs.values());
        }

        for (StageDoc doc : stageOrder) {
            JsonNode stage = doc.node();
            if (stage == null) {
                continue;
            }
            String file = doc.file();
            checkSchema(stage, ContentSchemas.STAGE, file, report);
            String fileKey = doc.fileKey();
            if (stage.path("stageKey").isString()
                    && !stage.get("stageKey").asString().equals(fileKey)) {
                report.add(
                        file,
                        "/stageKey",
                        stage.get("stageKey").asString(),
                        ContentRule.FILE_NAME_MISMATCH,
                        "O stageKey "
                                + stage.get("stageKey").asString()
                                + " difere do nome do arquivo "
                                + fileKey
                                + JSON
                                + ".");
            }
            checkDocument(stage, ContentSchemas.STAGE, file, scope, report);
            JsonNode activities = stage.path("activities");
            for (int i = 0; i < activities.size(); i++) {
                String type = activities.get(i).path("type").asString(null);
                // Tipo fora da união já é erro de schema.
                if (type != null && this.activityTypes.contains(type)) {
                    for (Map<String, ActivityRule> rules :
                            List.of(ACCEPTED_ANSWER_RULES, TYPE_RULES)) {
                        ActivityRule rule = rules.get(type);
                        if (rule != null) {
                            rule.check(activities.get(i), "/activities/" + i, file, report);
                        }
                    }
                }
            }
        }

        scope.unitKeys.forEach((name, values) ->
                values.forEach((key, places) -> {
                    if (places.size() > 1) {
                        for (Located place : places) {
                            report.add(
                                    place.file(),
                                    place.pointer(),
                                    key,
                                    ContentRule.DUPLICATE_KEY,
                                    "O "
                                            + name
                                            + " "
                                            + key
                                            + " se repete na Unidade ("
                                            + places.size()
                                            + " vezes); ele precisa ser único.");
                        }
                    }
                }));
        return unit;
    }

    /** Regras que valem para qualquer arquivo da Unidade: textos, mídia e chaves. */
    private void checkDocument(
            JsonNode document, String schemaId, String file, Scope scope, Report report) {
        this.walker.walkTexts(
                document,
                schemaId,
                (pointer, text) ->
                        checkText(document, pointer, text, file, scope.expressionKeys, report));
        scan(document, "", file, scope, report);
    }

    private void checkText(
            JsonNode document,
            String pointer,
            JsonNode text,
            String file,
            Set<String> expressionKeys,
            Report report) {
        if (text.isString()) {
            checkMarkup(document, pointer, text.asString(), file, expressionKeys, report);
            return;
        }
        if (!text.isObject()) {
            return;
        }
        // Variante presente com tipo errado é erro de schema, não variante ausente.
        List<String> missing =
                Stream.of("he", "she", "they").filter(variant -> !text.has(variant)).toList();
        if (!missing.isEmpty()) {
            report.add(
                    file,
                    pointer,
                    JsonPointers.nearestKey(document, pointer),
                    ContentRule.PRONOUN_VARIANTS_MISSING,
                    "Texto com pronome precisa das três variantes (he, she, they); falta "
                            + String.join(", ", missing)
                            + ".");
        }
        for (var variant : text.properties()) {
            if (variant.getValue().isString()) {
                checkMarkup(
                        document,
                        pointer + "/" + JsonPointers.escape(variant.getKey()),
                        variant.getValue().asString(),
                        file,
                        expressionKeys,
                        report);
            }
        }
    }

    private void checkMarkup(
            JsonNode document,
            String pointer,
            String text,
            String file,
            Set<String> expressionKeys,
            Report report) {
        for (MarkupChecker.Problem problem : this.markup.check(text, expressionKeys)) {
            report.add(
                    file,
                    pointer,
                    JsonPointers.nearestKey(document, pointer),
                    problem.rule(),
                    problem.message());
        }
    }

    /** Mídia no manifesto e chaves únicas, em qualquer profundidade. */
    private void scan(JsonNode node, String pointer, String file, Scope scope, Report report) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                String name = property.getKey();
                JsonNode value = property.getValue();
                String child = pointer + "/" + JsonPointers.escape(name);
                if (value.isString() && "mediaKey".equals(name)) {
                    scope.referencedMedia.add(value.asString());
                }
                if (value.isString()
                        && "mediaKey".equals(name)
                        && !scope.mediaKeys.contains(value.asString())) {
                    report.add(
                            file,
                            child,
                            value.asString(),
                            ContentRule.MEDIA_NOT_FOUND,
                            "O mediaKey "
                                    + value.asString()
                                    + " não existe em content/"
                                    + MEDIA_FILE
                                    + ".");
                }
                if (value.isString() && UNIT_SCOPED_KEYS.contains(name)) {
                    scope.unitKeys
                            .computeIfAbsent(name, key -> new LinkedHashMap<>())
                            .computeIfAbsent(value.asString(), key -> new ArrayList<>())
                            .add(new Located(file, child, name));
                }
                scan(value, child, file, scope, report);
            }
        } else if (node.isArray()) {
            for (String name : LIST_SCOPED_KEYS) {
                Map<String, List<Integer>> positions = new LinkedHashMap<>();
                for (int i = 0; i < node.size(); i++) {
                    if (node.get(i).path(name).isString()) {
                        positions
                                .computeIfAbsent(
                                        node.get(i).get(name).asString(), key -> new ArrayList<>())
                                .add(i);
                    }
                }
                positions.forEach((key, indexes) -> {
                    if (indexes.size() > 1) {
                        for (int i : indexes) {
                            report.add(
                                    file,
                                    pointer + "/" + i + "/" + name,
                                    key,
                                    ContentRule.DUPLICATE_KEY,
                                    "O "
                                            + name
                                            + " "
                                            + key
                                            + " se repete na mesma lista; ele precisa ser único.");
                        }
                    }
                });
            }
            for (int i = 0; i < node.size(); i++) {
                scan(node.get(i), pointer + "/" + i, file, scope, report);
            }
        }
    }

    /**
     * Resposta aceita da Escolha: {@code correctOptionKey} existe e está entre as opções do item.
     */
    private static void checkChoiceAnswer(
            JsonNode activity, String pointer, String file, Report report) {
        JsonNode items = activity.path("items");
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String at = pointer + "/items/" + i + "/correctOptionKey";
            String itemKey = item.path("itemKey").asString(null);
            JsonNode correct = item.path("correctOptionKey");
            if (!correct.isString()) {
                report.add(
                        file,
                        at,
                        itemKey,
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "O item não tem correctOptionKey (a resposta aceita).");
                continue;
            }
            boolean found = false;
            for (JsonNode option : item.path("options")) {
                found |= correct.asString().equals(option.path("optionKey").asString(null));
            }
            if (!found) {
                report.add(
                        file,
                        at,
                        itemKey,
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "O correctOptionKey "
                                + correct.asString()
                                + " não está entre as opções do item.");
            }
        }
    }

    /**
     * Perguntas do Diálogo: cada {@code ask} ({@code name}, {@code country}) aparece no máximo uma
     * vez na Atividade. Valor fora do {@code enum} já é erro de schema.
     */
    private static void checkDialogueAsks(
            JsonNode activity, String pointer, String file, Report report) {
        Map<String, List<Integer>> asks = new LinkedHashMap<>();
        JsonNode lines = activity.path("lines");
        if (!lines.isArray()) {
            // lines fora do formato já é erro de schema.
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            JsonNode ask = lines.get(i).path("ask");
            if (ask.isString()) {
                asks.computeIfAbsent(ask.asString(), key -> new ArrayList<>()).add(i);
            }
        }
        asks.forEach((ask, positions) -> {
            if (positions.size() > 1) {
                for (int i : positions) {
                    report.add(
                            file,
                            pointer + "/lines/" + i + "/ask",
                            lines.get(i).path("lineKey").asString(null),
                            ContentRule.DUPLICATE_ASK,
                            "A pergunta "
                                    + ask
                                    + " aparece em "
                                    + positions.size()
                                    + " falas do Diálogo; ela pode aparecer no máximo uma vez.");
                }
            }
        });
    }

    /**
     * Frase da Expressão Útil destacada (história 1.14): ao menos um termo {@code
     * [[texto|expressionKey]]}, em cada variante de pronome. Frase fora do formato já é erro de
     * schema.
     */
    private static void checkExpressionTerm(
            JsonNode activity, String pointer, String file, Report report) {
        JsonNode sentence = activity.path("sentence");
        Map<String, JsonNode> texts = new LinkedHashMap<>();
        if (sentence.isString()) {
            texts.put("", sentence);
        } else if (sentence.isObject()) {
            for (var variant : sentence.properties()) {
                texts.put("/" + JsonPointers.escape(variant.getKey()), variant.getValue());
            }
        }
        texts.forEach((at, text) -> {
            if (text.isString() && !TERM.matcher(text.asString()).find()) {
                report.add(
                        file,
                        pointer + "/sentence" + at,
                        activity.path("activityKey").asString(null),
                        ContentRule.MISSING_EXPRESSION_TERM,
                        "A frase da Expressão Útil destacada precisa de ao menos um termo"
                                + " [[texto|expressionKey]].");
            }
        });
    }

    /**
     * Frases da aba "Você já usou" do Grammar Time (história 1.19): cada uma tem ao menos um trecho
     * {@code **...**}, a forma destacada, em cada variante de pronome. O destaque vem do conteúdo;
     * o código não o calcula. Lista ou frase fora do formato já é erro de schema.
     */
    private static void checkGrammarHighlight(
            JsonNode activity, String pointer, String file, Report report) {
        JsonNode used = activity.path("used");
        if (!used.isArray()) {
            return;
        }
        for (int i = 0; i < used.size(); i++) {
            JsonNode sentence = used.get(i).path("sentence");
            String at = pointer + "/used/" + i + "/sentence";
            String key = used.get(i).path("sentenceKey").asString(null);
            Map<String, JsonNode> texts = new LinkedHashMap<>();
            if (sentence.isString()) {
                texts.put("", sentence);
            } else if (sentence.isObject()) {
                for (var variant : sentence.properties()) {
                    texts.put(variant.getKey(), variant.getValue());
                }
            }
            texts.forEach((variant, text) -> {
                if (text.isString() && MarkupChecker.lacksBold(text.asString())) {
                    report.add(
                            file,
                            variant.isEmpty() ? at : at + "/" + JsonPointers.escape(variant),
                            key,
                            ContentRule.MISSING_GRAMMAR_HIGHLIGHT,
                            "A frase do Grammar Time precisa destacar a forma em **negrito**"
                                    + (variant.isEmpty() ? "." : ", na variante " + variant + "."));
                }
            });
        }
    }

    /** Resposta aceita da Fala (história 1.15): a frase-alvo existe. */
    private static void checkSpeechAnswer(
            JsonNode activity, String pointer, String file, Report report) {
        if (!activity.has("target")) {
            report.add(
                    file,
                    pointer + "/target",
                    activity.path("activityKey").asString(null),
                    ContentRule.MISSING_ACCEPTED_ANSWER,
                    "A Fala não tem target (a frase-alvo, que é a resposta aceita).");
        }
    }

    /**
     * Resposta aceita do Associar (história 1.16): cada {@code correctResponseKey} existe e está em
     * {@code responses}, e dois itens não usam a mesma resposta. Sem isso, o par único da UI
     * deixaria a Atividade sem solução. Lista fora do formato já é erro de schema.
     */
    private static void checkMatchAnswer(
            JsonNode activity, String pointer, String file, Report report) {
        JsonNode items = activity.path("items");
        if (!items.isArray()) {
            return;
        }
        JsonNode responses = activity.path("responses");
        Set<String> responseKeys = new HashSet<>();
        for (JsonNode response : responses) {
            if (response.path("responseKey").isString()) {
                responseKeys.add(response.get("responseKey").asString());
            }
        }
        Map<String, List<Integer>> uses = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String at = pointer + "/items/" + i + "/correctResponseKey";
            String itemKey = item.path("itemKey").asString(null);
            JsonNode correct = item.path("correctResponseKey");
            if (!correct.isString()) {
                report.add(
                        file,
                        at,
                        itemKey,
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "O item não tem um correctResponseKey válido (a resposta aceita).");
                continue;
            }
            uses.computeIfAbsent(correct.asString(), key -> new ArrayList<>()).add(i);
            // responses fora de lista já é erro de schema; não repete o erro em cada item.
            if (responses.isArray() && !responseKeys.contains(correct.asString())) {
                report.add(
                        file,
                        at,
                        itemKey,
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "O correctResponseKey "
                                + correct.asString()
                                + " não está entre as respostas (responses).");
            }
        }
        uses.forEach((key, positions) -> {
            if (positions.size() > 1) {
                for (int i : positions) {
                    report.add(
                            file,
                            pointer + "/items/" + i + "/correctResponseKey",
                            items.get(i).path("itemKey").asString(null),
                            ContentRule.MISSING_ACCEPTED_ANSWER,
                            "O correctResponseKey "
                                    + key
                                    + " se repete em "
                                    + positions.size()
                                    + " itens; cada item precisa de uma resposta própria.");
                }
            }
        });
    }

    /**
     * Resposta aceita das Lacunas (história 1.17): cada item tem {@code accepted}, e no {@code
     * bank} ao menos uma resposta aceita de cada item está no banco, com palavras para todos os
     * itens ao mesmo tempo (um emparelhamento item a palavra), em cada variante de pronome. A
     * comparação aqui é mais estrita que a do corretor (só caixa e espaços): o que passa aqui, o
     * corretor aceita. Lista fora do formato já é erro de schema.
     */
    private static void checkGapsAnswer(
            JsonNode activity, String pointer, String file, Report report) {
        checkGapItemsAnswer(
                activity,
                pointer,
                activity.path("activityKey").asString(null),
                GapsActivity.MODE_BANK.equals(activity.path("mode").asString(null)),
                file,
                report);
    }

    /**
     * Resposta aceita da Resposta Livre (história 1.18): a resposta-modelo existe, e o Extra Help
     * segue a regra das Lacunas do {@code bank} ({@link #checkGapsAnswer}). Extra Help fora do
     * formato já é erro de schema.
     */
    private static void checkFreeAnswer(
            JsonNode activity, String pointer, String file, Report report) {
        String activityKey = activity.path("activityKey").asString(null);
        if (!activity.has("modelAnswer")) {
            report.add(
                    file,
                    pointer + "/modelAnswer",
                    activityKey,
                    ContentRule.MISSING_ACCEPTED_ANSWER,
                    "A Resposta Livre não tem modelAnswer (a resposta-modelo).");
        }
        JsonNode extraHelp = activity.path("extraHelp");
        if (extraHelp.isObject()) {
            checkGapItemsAnswer(extraHelp, pointer + "/extraHelp", activityKey, true, file, report);
        }
    }

    /**
     * A regra de resposta aceita das lacunas sobre um nó com {@code items} e {@code bank} (a
     * Atividade das Lacunas ou o Extra Help da Resposta Livre), em {@code pointer}.
     */
    private static void checkGapItemsAnswer(
            JsonNode activity,
            String pointer,
            String activityKey,
            boolean bankMode,
            String file,
            Report report) {
        JsonNode items = activity.path("items");
        if (!items.isArray()) {
            return;
        }
        boolean complete = true;
        for (int i = 0; i < items.size(); i++) {
            JsonNode accepted = items.get(i).path("accepted");
            if (!accepted.isArray() || accepted.isEmpty()) {
                complete = false;
                report.add(
                        file,
                        pointer + "/items/" + i + "/accepted",
                        items.get(i).path("itemKey").asString(null),
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "O item não tem accepted (as respostas aceitas).");
            }
        }
        JsonNode bank = activity.path("bank");
        if (!complete || !bankMode || !bank.isArray()) {
            // Sem banco no bank é BANK_MODE_MISMATCH; banco fora de lista é erro de schema.
            return;
        }
        // Só o banco muda por pronome aqui (a resposta aceita vale em qualquer variante).
        boolean variants = false;
        for (JsonNode word : bank) {
            variants |= word.path("text").isObject();
        }
        Map<Integer, List<String>> uncovered = new TreeMap<>();
        List<String> notEnough = new ArrayList<>();
        for (String pronoun : PRONOUNS) {
            List<Set<Integer>> candidates = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Set<String> forms = new HashSet<>();
                for (JsonNode accepted : items.get(i).path("accepted")) {
                    for (String pronounForm : PRONOUNS) {
                        String form = variant(accepted, pronounForm);
                        if (form != null) {
                            forms.add(bankForm(form));
                        }
                    }
                }
                Set<Integer> words = new HashSet<>();
                for (int w = 0; w < bank.size(); w++) {
                    String text = variant(bank.get(w).path("text"), pronoun);
                    if (text != null && forms.contains(bankForm(text))) {
                        words.add(w);
                    }
                }
                if (words.isEmpty()) {
                    uncovered.computeIfAbsent(i, key -> new ArrayList<>()).add(pronoun);
                }
                candidates.add(words);
            }
            if (maxMatching(candidates, bank.size()) < items.size()) {
                notEnough.add(pronoun);
            }
        }
        boolean withVariants = variants;
        uncovered.forEach((i, pronouns) ->
                report.add(
                        file,
                        pointer + "/items/" + i + "/accepted",
                        items.get(i).path("itemKey").asString(null),
                        ContentRule.MISSING_ACCEPTED_ANSWER,
                        "Nenhuma resposta aceita do item está no banco (bank)"
                                + inVariants(withVariants, pronouns)
                                + "."));
        if (uncovered.isEmpty() && !notEnough.isEmpty()) {
            report.add(
                    file,
                    pointer + "/bank",
                    activityKey,
                    ContentRule.MISSING_ACCEPTED_ANSWER,
                    "O banco (bank) não tem palavras para todos os itens ao mesmo tempo"
                            + inVariants(variants, notEnough)
                            + ".");
        }
    }

    /** ", na variante she" (ou "nas variantes he, she") quando o conteúdo tem variantes. */
    private static String inVariants(boolean variants, List<String> pronouns) {
        if (!variants) {
            return "";
        }
        return (pronouns.size() == 1 ? ", na variante " : ", nas variantes ")
                + String.join(", ", pronouns);
    }

    /**
     * Forma e banco das Lacunas (história 1.17): o número de lacunas ({@code ___}) nas linhas é
     * igual ao de itens em cada variante de pronome, e o {@code bank} existe só no {@code mode}
     * {@code bank}. Lista fora do formato já é erro de schema.
     */
    private static void checkGapsShape(
            JsonNode activity, String pointer, String file, Report report) {
        String activityKey = activity.path("activityKey").asString(null);
        checkGapCount(activity, pointer, activityKey, file, report);
        String mode = activity.path("mode").asString(null);
        boolean hasBank = activity.has("bank");
        if (GapsActivity.MODE_TYPED.equals(mode) && hasBank) {
            report.add(
                    file,
                    pointer + "/bank",
                    activityKey,
                    ContentRule.BANK_MODE_MISMATCH,
                    "O banco (bank) existe só no mode bank.");
        } else if (GapsActivity.MODE_BANK.equals(mode) && !hasBank) {
            report.add(
                    file,
                    pointer + "/bank",
                    activityKey,
                    ContentRule.BANK_MODE_MISMATCH,
                    "O mode bank precisa do banco (bank).");
        }
    }

    /**
     * Forma do Extra Help da Resposta Livre (história 1.18): o número de lacunas nas linhas é igual
     * ao de itens, como nas Lacunas. O banco é obrigatório no schema.
     */
    private static void checkFreeShape(
            JsonNode activity, String pointer, String file, Report report) {
        JsonNode extraHelp = activity.path("extraHelp");
        if (extraHelp.isObject()) {
            checkGapCount(
                    extraHelp,
                    pointer + "/extraHelp",
                    activity.path("activityKey").asString(null),
                    file,
                    report);
        }
    }

    /**
     * O número de lacunas ({@code ___}) nas {@code lines} do nó é igual ao de {@code items} em cada
     * variante de pronome. Lista fora do formato já é erro de schema.
     */
    private static void checkGapCount(
            JsonNode activity, String pointer, String activityKey, String file, Report report) {
        JsonNode lines = activity.path("lines");
        JsonNode items = activity.path("items");
        if (lines.isArray() && items.isArray()) {
            Set<String> mismatches = new LinkedHashSet<>();
            for (String pronoun : PRONOUNS) {
                int gaps = 0;
                for (JsonNode line : lines) {
                    String text = variant(line, pronoun);
                    if (text != null) {
                        Matcher gap = GAP.matcher(text);
                        while (gap.find()) {
                            gaps++;
                        }
                    }
                }
                if (gaps != items.size()) {
                    mismatches.add(
                            gaps + " lacuna" + ((gaps == 1) ? "" : "s") + " (" + pronoun + ")");
                }
            }
            if (!mismatches.isEmpty()) {
                report.add(
                        file,
                        pointer + "/lines",
                        activityKey,
                        ContentRule.GAP_COUNT_MISMATCH,
                        "As linhas têm "
                                + String.join(", ", mismatches)
                                + " para "
                                + items.size()
                                + " itens; cada ___ precisa de um item, na ordem.");
            }
        }
    }

    /** A variante de pronome do texto; a string vale para todas. {@code null} fora do formato. */
    private static String variant(JsonNode text, String pronoun) {
        if (text.isString()) {
            return text.asString();
        }
        JsonNode variant = text.path(pronoun);
        return variant.isString() ? variant.asString() : null;
    }

    /** O texto comparado entre o banco e as respostas aceitas: sem caixa e espaços colapsados. */
    private static String bankForm(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** O maior emparelhamento item a palavra (caminhos aumentantes; são poucos itens). */
    private static int maxMatching(List<Set<Integer>> candidates, int words) {
        int[] owner = new int[words];
        Arrays.fill(owner, -1);
        int matched = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (augment(i, candidates, owner, new boolean[words])) {
                matched++;
            }
        }
        return matched;
    }

    private static boolean augment(
            int item, List<Set<Integer>> candidates, int[] owner, boolean[] seen) {
        for (int word : candidates.get(item)) {
            if (!seen[word]) {
                seen[word] = true;
                if (owner[word] < 0 || augment(owner[word], candidates, owner, seen)) {
                    owner[word] = item;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Frase-alvo e alternativas da Fala (história 1.15): cada uma, em cada variante de pronome, tem
     * ao menos uma palavra fora dos placeholders. Texto fora do formato já é erro de schema.
     */
    private static void checkSpeechWords(
            JsonNode activity, String pointer, String file, Report report) {
        Map<String, JsonNode> texts = new LinkedHashMap<>();
        texts.put("/target", activity.path("target"));
        JsonNode alternatives = activity.path("alternatives");
        // As regras rodam mesmo com erro de schema: alternatives fora de lista já é erro de lá.
        if (alternatives.isArray()) {
            for (int i = 0; i < alternatives.size(); i++) {
                texts.put("/alternatives/" + i, alternatives.get(i));
            }
        }
        texts.forEach((at, text) -> {
            Map<String, JsonNode> variants = new LinkedHashMap<>();
            if (text == null) {
                return;
            }
            if (text.isString()) {
                variants.put(at, text);
            } else if (text.isObject()) {
                for (var variant : text.properties()) {
                    variants.put(
                            at + "/" + JsonPointers.escape(variant.getKey()), variant.getValue());
                }
            }
            variants.forEach((where, variant) -> {
                if (variant.isString() && !hasSpeechWord(variant.asString())) {
                    report.add(
                            file,
                            pointer + where,
                            activity.path("activityKey").asString(null),
                            ContentRule.MISSING_SPEECH_WORD,
                            "A frase da Fala precisa de ao menos uma palavra fora dos"
                                    + " placeholders.");
                }
            });
        });
    }

    /** Se o texto tem uma palavra fora dos placeholders; de um termo, conta só o texto. */
    private static boolean hasSpeechWord(String text) {
        String plain =
                TERM.matcher(text)
                        .replaceAll(match -> Matcher.quoteReplacement(" " + match.group(1) + " "));
        plain = PLACEHOLDER.matcher(plain).replaceAll(" ");
        return WORD_CHAR.matcher(plain).find();
    }

    private void checkSchema(JsonNode document, String schemaId, String file, Report report) {
        for (Error error : this.schemas.schema(schemaId).validate(document)) {
            String pointer = JsonPointers.of(error.getInstanceLocation());
            if ("required".equals(error.getKeyword()) && error.getProperty() != null) {
                pointer = pointer + "/" + JsonPointers.escape(error.getProperty());
            }
            report.addSchema(
                    file,
                    pointer,
                    JsonPointers.nearestKey(document, pointer),
                    error.getKeyword(),
                    error.getMessage());
        }
    }

    private static JsonNode read(Path file, Report report) {
        if (!Files.isRegularFile(file)) {
            report.add(file, "", null, ContentRule.MISSING_FILE, "Arquivo obrigatório ausente.");
            return null;
        }
        try {
            JsonNode node = (Files.size(file) == 0) ? null : MAPPER.readTree(file.toFile());
            if (node == null || node.isMissingNode()) {
                report.add(file, "", null, ContentRule.INVALID_JSON, "Arquivo vazio.");
                return null;
            }
            return node;
        } catch (JacksonException ex) {
            TokenStreamLocation location = ex.getLocation();
            String where =
                    (location != null)
                            ? " (linha "
                                    + location.getLineNr()
                                    + ", coluna "
                                    + location.getColumnNr()
                                    + ")"
                            : "";
            report.add(
                    file,
                    "",
                    null,
                    ContentRule.INVALID_JSON,
                    "JSON inválido" + where + ": " + ex.getOriginalMessage());
            return null;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** As entradas da pasta, sem as ocultas ({@code .gitkeep}, {@code .DS_Store}). */
    private static List<Path> list(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(entry -> !entry.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @FunctionalInterface
    private interface ActivityRule {

        void check(JsonNode activity, String pointer, String file, Report report);
    }

    private record Located(String file, String pointer, String name) {}

    /** Um Estágio já lido: o arquivo do relatório, a chave do nome do arquivo e o documento. */
    private record StageDoc(String file, String fileKey, JsonNode node) {}

    /** O que as regras acumulam ao percorrer uma Unidade. */
    private static final class Scope {

        final Set<String> mediaKeys;

        /** Os {@code mediaKey} que a Unidade usa. */
        final Set<String> referencedMedia = new HashSet<>();

        /**
         * As chaves de {@code usefulExpressions}; {@code null} sem a Unidade, e aí a existência da
         * chave não é conferida.
         */
        Set<String> expressionKeys;

        final Map<String, Map<String, List<Located>>> unitKeys = new LinkedHashMap<>();

        Scope(Set<String> mediaKeys) {
            this.mediaKeys = mediaKeys;
        }
    }

    /** Ordena ponteiros pelos segmentos, com os índices em ordem numérica. */
    static final Comparator<String> POINTER_ORDER =
            (left, right) -> {
                String[] a = left.split("/", -1);
                String[] b = right.split("/", -1);
                for (int i = 0; i < Math.min(a.length, b.length); i++) {
                    int compared =
                            (a[i].matches("\\d+") && b[i].matches("\\d+"))
                                    ? Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]))
                                    : a[i].compareTo(b[i]);
                    if (compared != 0) {
                        return compared;
                    }
                }
                return Integer.compare(a.length, b.length);
            };

    /**
     * Junta os erros. Um erro de schema que só repete uma regra própria sai do relatório: o {@code
     * required} de {@code correctOptionKey} e de {@code correctResponseKey} (coberto por {@code
     * MISSING_ACCEPTED_ANSWER}) e o {@code oneOf}, o {@code type} da alternativa string e o {@code
     * required} da variante faltando (cobertos por {@code PRONOUN_VARIANTS_MISSING}). Qualquer
     * outro erro de schema fica.
     */
    private static final class Report {

        private final Path base;

        private final List<ContentError> rules = new ArrayList<>();

        private final List<SchemaError> schema = new ArrayList<>();

        Report(Path base) {
            this.base = base.toAbsolutePath();
        }

        void add(Path file, String pointer, String key, ContentRule rule, String message) {
            add(name(file), pointer, key, rule, message);
        }

        void add(String file, String pointer, String key, ContentRule rule, String message) {
            this.rules.add(new ContentError(file, pointer, key, rule, message));
        }

        void addSchema(String file, String pointer, String key, String keyword, String message) {
            this.schema.add(
                    new SchemaError(
                            new ContentError(
                                    file, pointer, key, ContentRule.SCHEMA_VIOLATION, message),
                            keyword));
        }

        List<ContentError> errors() {
            List<ContentError> all = new ArrayList<>(this.rules);
            for (SchemaError error : this.schema) {
                boolean covered = this.rules.stream().anyMatch(rule -> covers(rule, error));
                if (!covered && !all.contains(error.error())) {
                    all.add(error.error());
                }
            }
            all.sort(
                    Comparator.comparing(ContentError::file)
                            .thenComparing(ContentError::pointer, POINTER_ORDER)
                            .thenComparing(ContentError::rule)
                            .thenComparing(ContentError::message));
            return List.copyOf(all);
        }

        private static boolean covers(ContentError rule, SchemaError schema) {
            if (!rule.file().equals(schema.error().file())) {
                return false;
            }
            String at = rule.pointer();
            String pointer = schema.error().pointer();
            return switch (rule.rule()) {
                case MISSING_ACCEPTED_ANSWER ->
                        pointer.equals(at) && "required".equals(schema.keyword());
                case PRONOUN_VARIANTS_MISSING ->
                        (pointer.equals(at)
                                        && ("oneOf".equals(schema.keyword())
                                                || "type".equals(schema.keyword())))
                                || ("required".equals(schema.keyword())
                                        && pointer.startsWith(at + "/")
                                        && pointer.indexOf('/', at.length() + 1) < 0);
                default -> false;
            };
        }

        String name(Path file) {
            Path absolute = file.toAbsolutePath();
            Path relative =
                    absolute.startsWith(this.base) ? this.base.relativize(absolute) : absolute;
            return relative.toString().replace('\\', '/');
        }

        private record SchemaError(ContentError error, String keyword) {}
    }
}
