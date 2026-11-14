package com.fluveny.fluveny_backend.content.validation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.networknt.schema.Error;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valida o Conteúdo Estruturado (FR-29, AD-4, AD-5): o JSON Schema de
 * {@code content/schema/} e as regras que ele não expressa (resposta aceita,
 * mídia no manifesto, variantes de pronome, formatação, chaves únicas e
 * arquivos de Estágio). Devolve todos os erros de uma vez, cada um com arquivo,
 * ponteiro JSON, chave e regra. Roda nos testes, portanto na CI
 * ({@code ContentUnitsTest}).
 */
public final class ContentValidator {

	static final String UNIT_FILE = "unit.json";

	static final String STAGES_DIR = "stages";

	static final String MEDIA_FILE = "media.json";

	static final String UNITS_DIR = "units";

	private static final String JSON = ".json";

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	/** Chaves únicas dentro da própria lista (a do Item, a das Expressões Úteis). */
	private static final Set<String> LIST_SCOPED_KEYS = Set.of("optionKey", "expressionKey");

	/** Chaves únicas na Unidade inteira (AD-5), cada uma no próprio espaço. */
	private static final Set<String> UNIT_SCOPED_KEYS = Set.of("activityKey", "itemKey");

	/**
	 * A regra de resposta aceita de cada Tipo (FR-29). Todo {@code type} da união
	 * do schema precisa de uma; o construtor falha se faltar.
	 */
	private static final Map<String, AcceptedAnswerRule> ACCEPTED_ANSWER_RULES = Map.of(ChoiceActivity.TYPE,
			ContentValidator::checkChoiceAnswer);

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
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Tipo sem regra de resposta aceita no ContentValidator: " + missing);
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
	 * Valida uma pasta de conteúdo inteira: {@code media.json} e cada
	 * {@code units/<unitKey>/}. Os arquivos do relatório são relativos a
	 * {@code contentRoot}.
	 */
	public List<ContentError> validate(Path contentRoot) {
		Report report = new Report(contentRoot);
		Set<String> mediaKeys = readManifest(contentRoot.resolve(MEDIA_FILE), report);
		Path units = contentRoot.resolve(UNITS_DIR);
		Map<Long, List<Located>> orders = new TreeMap<>();
		if (Files.isDirectory(units)) {
			for (Path entry : list(units)) {
				if (!Files.isDirectory(entry)) {
					report.add(entry, "", null, ContentRule.UNEXPECTED_FILE,
							"Arquivo solto em " + UNITS_DIR + "/: cada Unidade é uma pasta " + UNITS_DIR + "/<unitKey>/.");
					continue;
				}
				JsonNode unit = checkUnit(entry, mediaKeys, report);
				if (unit != null && unit.path("order").isIntegralNumber()) {
					orders.computeIfAbsent(unit.get("order").asLong(), order -> new ArrayList<>())
						.add(new Located(entry.resolve(UNIT_FILE), "/order", unit.path("unitKey").asString(null)));
				}
			}
		}
		orders.forEach((order, places) -> {
			if (places.size() > 1) {
				for (Located place : places) {
					report.add(place.file(), place.pointer(), place.name(), ContentRule.DUPLICATE_KEY,
							"O order " + order + " se repete em " + places.size() + " Unidades; ele precisa ser único.");
				}
			}
		});
		return report.errors();
	}

	/**
	 * Valida uma pasta de Unidade contra o manifesto de mídia. Os arquivos do
	 * relatório são relativos à pasta pai da Unidade.
	 */
	public List<ContentError> validateUnit(Path unitDir, Path mediaManifest) {
		Report report = new Report(unitDir.toAbsolutePath().getParent());
		Set<String> mediaKeys = readManifest(mediaManifest, report);
		checkUnit(unitDir, mediaKeys, report);
		return report.errors();
	}

	private Set<String> readManifest(Path manifest, Report report) {
		JsonNode node = read(manifest, report);
		if (node == null) {
			return Set.of();
		}
		checkSchema(node, ContentSchemas.MEDIA, manifest, report);
		Set<String> keys = new HashSet<>();
		if (node.isObject()) {
			keys.addAll(node.propertyNames());
		}
		return keys;
	}

	/** @return o {@code unit.json} lido, ou {@code null} se não deu para ler */
	private JsonNode checkUnit(Path unitDir, Set<String> mediaKeys, Report report) {
		Path unitFile = unitDir.resolve(UNIT_FILE);
		JsonNode unit = read(unitFile, report);

		Map<String, Path> stageFiles = new TreeMap<>();
		Path stagesDir = unitDir.resolve(STAGES_DIR);
		if (Files.isDirectory(stagesDir)) {
			for (Path file : list(stagesDir)) {
				String name = file.getFileName().toString();
				if (Files.isRegularFile(file) && name.endsWith(JSON)) {
					stageFiles.put(name.substring(0, name.length() - JSON.length()), file);
				}
				else {
					report.add(file, "", null, ContentRule.UNEXPECTED_FILE, "Em " + STAGES_DIR
							+ "/ só ficam os arquivos <stageKey>" + JSON + " (extensão em minúsculas, sem subpastas).");
				}
			}
		}

		// Sem a Unidade não se sabe quais expressionKey existem: a existência não é conferida.
		Set<String> expressionKeys = null;
		Map<String, Map<String, List<Located>>> unitKeys = new LinkedHashMap<>();
		List<Path> stageOrder = new ArrayList<>();

		if (unit != null) {
			expressionKeys = new HashSet<>();
			checkSchema(unit, ContentSchemas.UNIT, unitFile, report);
			String dirName = unitDir.getFileName().toString();
			if (unit.path("unitKey").isString() && !unit.get("unitKey").asString().equals(dirName)) {
				report.add(unitFile, "/unitKey", unit.get("unitKey").asString(), ContentRule.FILE_NAME_MISMATCH,
						"O unitKey " + unit.get("unitKey").asString() + " difere do nome da pasta " + dirName + ".");
			}
			for (JsonNode expression : unit.path("usefulExpressions")) {
				if (expression.path("expressionKey").isString()) {
					expressionKeys.add(expression.get("expressionKey").asString());
				}
			}

			Map<String, List<Integer>> listed = new LinkedHashMap<>();
			JsonNode stages = unit.path("stages");
			for (int i = 0; i < stages.size(); i++) {
				if (stages.get(i).isString()) {
					listed.computeIfAbsent(stages.get(i).asString(), key -> new ArrayList<>()).add(i);
				}
			}
			listed.forEach((stageKey, positions) -> {
				if (positions.size() > 1) {
					for (int position : positions) {
						report.add(unitFile, "/stages/" + position, stageKey, ContentRule.DUPLICATE_KEY,
								"O stageKey " + stageKey + " aparece " + positions.size() + " vezes em stages.");
					}
				}
				Path file = stageFiles.get(stageKey);
				if (file == null) {
					report.add(unitFile, "/stages/" + positions.get(0), stageKey, ContentRule.STAGE_FILE_MISMATCH,
							"O Estágio " + stageKey + " está em stages, mas falta o arquivo " + STAGES_DIR + "/"
									+ stageKey + JSON + ".");
				}
				else {
					stageOrder.add(file);
				}
			});
			stageFiles.forEach((stageKey, file) -> {
				if (!listed.containsKey(stageKey)) {
					report.add(file, "", stageKey, ContentRule.STAGE_FILE_MISMATCH,
							"O arquivo do Estágio " + stageKey + " não está em stages do " + UNIT_FILE + ".");
					stageOrder.add(file);
				}
			});
			checkDocument(unit, ContentSchemas.UNIT, unitFile, expressionKeys, mediaKeys, unitKeys, report);
		}
		else {
			stageOrder.addAll(stageFiles.values());
		}

		for (Path file : stageOrder) {
			JsonNode stage = read(file, report);
			if (stage == null) {
				continue;
			}
			checkSchema(stage, ContentSchemas.STAGE, file, report);
			String fileKey = file.getFileName().toString().replaceFirst("\\.json$", "");
			if (stage.path("stageKey").isString() && !stage.get("stageKey").asString().equals(fileKey)) {
				report.add(file, "/stageKey", stage.get("stageKey").asString(), ContentRule.FILE_NAME_MISMATCH,
						"O stageKey " + stage.get("stageKey").asString() + " difere do nome do arquivo " + fileKey
								+ JSON + ".");
			}
			checkDocument(stage, ContentSchemas.STAGE, file, expressionKeys, mediaKeys, unitKeys, report);
			JsonNode activities = stage.path("activities");
			for (int i = 0; i < activities.size(); i++) {
				String type = activities.get(i).path("type").asString(null);
				// Tipo fora da união já é erro de schema.
				if (type != null && this.activityTypes.contains(type)) {
					ACCEPTED_ANSWER_RULES.get(type).check(activities.get(i), "/activities/" + i, file, report);
				}
			}
		}

		unitKeys.forEach((name, values) -> values.forEach((key, places) -> {
			if (places.size() > 1) {
				for (Located place : places) {
					report.add(place.file(), place.pointer(), key, ContentRule.DUPLICATE_KEY,
							"O " + name + " " + key + " se repete na Unidade (" + places.size()
									+ " vezes); ele precisa ser único.");
				}
			}
		}));
		return unit;
	}

	/** Regras que valem para qualquer arquivo da Unidade: textos, mídia e chaves. */
	private void checkDocument(JsonNode document, String schemaId, Path file, Set<String> expressionKeys,
			Set<String> mediaKeys, Map<String, Map<String, List<Located>>> unitKeys, Report report) {
		this.walker.walkTexts(document, schemaId, (pointer, text) -> checkText(document, pointer, text, file,
				expressionKeys, report));
		scan(document, "", file, mediaKeys, unitKeys, report);
	}

	private void checkText(JsonNode document, String pointer, JsonNode text, Path file, Set<String> expressionKeys,
			Report report) {
		if (text.isString()) {
			checkMarkup(document, pointer, text.asString(), file, expressionKeys, report);
			return;
		}
		if (!text.isObject()) {
			return;
		}
		// Variante presente com tipo errado é erro de schema, não variante ausente.
		List<String> missing = Stream.of("he", "she", "they").filter(variant -> !text.has(variant)).toList();
		if (!missing.isEmpty()) {
			report.add(file, pointer, JsonPointers.nearestKey(document, pointer), ContentRule.PRONOUN_VARIANTS_MISSING,
					"Texto com pronome precisa das três variantes (he, she, they); falta " + String.join(", ", missing)
							+ ".");
		}
		for (var variant : text.properties()) {
			if (variant.getValue().isString()) {
				checkMarkup(document, pointer + "/" + JsonPointers.escape(variant.getKey()),
						variant.getValue().asString(), file, expressionKeys, report);
			}
		}
	}

	private void checkMarkup(JsonNode document, String pointer, String text, Path file, Set<String> expressionKeys,
			Report report) {
		for (MarkupChecker.Problem problem : this.markup.check(text, expressionKeys)) {
			report.add(file, pointer, JsonPointers.nearestKey(document, pointer), problem.rule(), problem.message());
		}
	}

	/** Mídia no manifesto e chaves únicas, em qualquer profundidade. */
	private void scan(JsonNode node, String pointer, Path file, Set<String> mediaKeys,
			Map<String, Map<String, List<Located>>> unitKeys, Report report) {
		if (node.isObject()) {
			for (var property : node.properties()) {
				String name = property.getKey();
				JsonNode value = property.getValue();
				String child = pointer + "/" + JsonPointers.escape(name);
				if (value.isString() && "mediaKey".equals(name) && !mediaKeys.contains(value.asString())) {
					report.add(file, child, value.asString(), ContentRule.MEDIA_NOT_FOUND,
							"O mediaKey " + value.asString() + " não existe em content/" + MEDIA_FILE + ".");
				}
				if (value.isString() && UNIT_SCOPED_KEYS.contains(name)) {
					unitKeys.computeIfAbsent(name, key -> new LinkedHashMap<>())
						.computeIfAbsent(value.asString(), key -> new ArrayList<>())
						.add(new Located(file, child, name));
				}
				scan(value, child, file, mediaKeys, unitKeys, report);
			}
		}
		else if (node.isArray()) {
			for (String name : LIST_SCOPED_KEYS) {
				Map<String, List<Integer>> positions = new LinkedHashMap<>();
				for (int i = 0; i < node.size(); i++) {
					if (node.get(i).path(name).isString()) {
						positions.computeIfAbsent(node.get(i).get(name).asString(), key -> new ArrayList<>()).add(i);
					}
				}
				positions.forEach((key, indexes) -> {
					if (indexes.size() > 1) {
						for (int i : indexes) {
							report.add(file, pointer + "/" + i + "/" + name, key, ContentRule.DUPLICATE_KEY,
									"O " + name + " " + key + " se repete na mesma lista; ele precisa ser único.");
						}
					}
				});
			}
			for (int i = 0; i < node.size(); i++) {
				scan(node.get(i), pointer + "/" + i, file, mediaKeys, unitKeys, report);
			}
		}
	}

	/** Resposta aceita da Escolha: {@code correctOptionKey} existe e está entre as opções do item. */
	private static void checkChoiceAnswer(JsonNode activity, String pointer, Path file, Report report) {
		JsonNode items = activity.path("items");
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String at = pointer + "/items/" + i + "/correctOptionKey";
			String itemKey = item.path("itemKey").asString(null);
			JsonNode correct = item.path("correctOptionKey");
			if (!correct.isString()) {
				report.add(file, at, itemKey, ContentRule.MISSING_ACCEPTED_ANSWER,
						"O item não tem correctOptionKey (a resposta aceita).");
				continue;
			}
			boolean found = false;
			for (JsonNode option : item.path("options")) {
				found |= correct.asString().equals(option.path("optionKey").asString(null));
			}
			if (!found) {
				report.add(file, at, itemKey, ContentRule.MISSING_ACCEPTED_ANSWER,
						"O correctOptionKey " + correct.asString() + " não está entre as opções do item.");
			}
		}
	}

	private void checkSchema(JsonNode document, String schemaId, Path file, Report report) {
		for (Error error : this.schemas.schema(schemaId).validate(document)) {
			String pointer = JsonPointers.of(error.getInstanceLocation());
			if ("required".equals(error.getKeyword()) && error.getProperty() != null) {
				pointer = pointer + "/" + JsonPointers.escape(error.getProperty());
			}
			report.addSchema(file, pointer, JsonPointers.nearestKey(document, pointer), error.getKeyword(),
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
		}
		catch (JacksonException ex) {
			TokenStreamLocation location = ex.getLocation();
			String where = (location != null) ? " (linha " + location.getLineNr() + ", coluna "
					+ location.getColumnNr() + ")" : "";
			report.add(file, "", null, ContentRule.INVALID_JSON, "JSON inválido" + where + ": " + ex.getOriginalMessage());
			return null;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** As entradas da pasta, sem as ocultas ({@code .gitkeep}, {@code .DS_Store}). */
	private static List<Path> list(Path dir) {
		try (Stream<Path> entries = Files.list(dir)) {
			return entries.filter(entry -> !entry.getFileName().toString().startsWith(".")).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@FunctionalInterface
	private interface AcceptedAnswerRule {

		void check(JsonNode activity, String pointer, Path file, Report report);

	}

	private record Located(Path file, String pointer, String name) {

	}

	/** Ordena ponteiros pelos segmentos, com os índices em ordem numérica. */
	static final Comparator<String> POINTER_ORDER = (left, right) -> {
		String[] a = left.split("/", -1);
		String[] b = right.split("/", -1);
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			int compared = (a[i].matches("\\d+") && b[i].matches("\\d+"))
					? Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i])) : a[i].compareTo(b[i]);
			if (compared != 0) {
				return compared;
			}
		}
		return Integer.compare(a.length, b.length);
	};

	/**
	 * Junta os erros. Um erro de schema que só repete uma regra própria sai do
	 * relatório: o {@code required} de {@code correctOptionKey} (coberto por
	 * {@code MISSING_ACCEPTED_ANSWER}) e o {@code oneOf}, o {@code type} da
	 * alternativa string e o {@code required} da variante faltando (cobertos por
	 * {@code PRONOUN_VARIANTS_MISSING}). Qualquer outro erro de schema fica.
	 */
	private static final class Report {

		private final Path base;

		private final List<ContentError> rules = new ArrayList<>();

		private final List<SchemaError> schema = new ArrayList<>();

		Report(Path base) {
			this.base = base.toAbsolutePath();
		}

		void add(Path file, String pointer, String key, ContentRule rule, String message) {
			this.rules.add(new ContentError(name(file), pointer, key, rule, message));
		}

		void addSchema(Path file, String pointer, String key, String keyword, String message) {
			this.schema.add(new SchemaError(
					new ContentError(name(file), pointer, key, ContentRule.SCHEMA_VIOLATION, message), keyword));
		}

		List<ContentError> errors() {
			List<ContentError> all = new ArrayList<>(this.rules);
			for (SchemaError error : this.schema) {
				boolean covered = this.rules.stream().anyMatch(rule -> covers(rule, error));
				if (!covered && !all.contains(error.error())) {
					all.add(error.error());
				}
			}
			all.sort(Comparator.comparing(ContentError::file)
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
				case MISSING_ACCEPTED_ANSWER -> pointer.equals(at) && "required".equals(schema.keyword());
				case PRONOUN_VARIANTS_MISSING -> (pointer.equals(at)
						&& ("oneOf".equals(schema.keyword()) || "type".equals(schema.keyword())))
						|| ("required".equals(schema.keyword()) && pointer.startsWith(at + "/")
								&& pointer.indexOf('/', at.length() + 1) < 0);
				default -> false;
			};
		}

		private String name(Path file) {
			Path absolute = file.toAbsolutePath();
			Path relative = absolute.startsWith(this.base) ? this.base.relativize(absolute) : absolute;
			return relative.toString().replace('\\', '/');
		}

		private record SchemaError(ContentError error, String keyword) {

		}

	}

}
