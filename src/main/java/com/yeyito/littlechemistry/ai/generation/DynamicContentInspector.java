package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentJson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** On-demand search and complete inspection of world-persisted generated content. */
final class DynamicContentInspector {
	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 100;

	private DynamicContentInspector() {
	}

	static ContentGenerationDraft.ToolExecution search(JsonObject arguments) {
		try {
			requireOnly(arguments, "query", "kind", "limit");
			String query = arguments.has("query") ? requiredString(arguments, "query").strip() : "";
			String kind = arguments.has("kind") ? requiredString(arguments, "kind").strip().toLowerCase(Locale.ROOT) : "any";
			int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : DEFAULT_LIMIT;
			if (query.length() > 160) throw new IllegalArgumentException("query must contain at most 160 characters");
			if (!Set.of("any", "item", "block", "armor", "entity").contains(kind)) {
				throw new IllegalArgumentException("kind must be any, item, block, armor, or entity");
			}
			if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);

			String normalizedQuery = query.toLowerCase(Locale.ROOT);
			List<String> tokens = List.of(normalizedQuery.split("[^a-z0-9_$.:-]+"));
			List<Match> matches = new ArrayList<>();
			for (DynamicContentDefinition definition : DynamicContentCatalog.definitions()) {
				if (!kind.equals("any") && !definition.type().serializedName().equals(kind)) continue;
				int score = score(definition, normalizedQuery, tokens);
				if (score >= 0) matches.add(new Match(definition, score));
			}
			matches.sort(Comparator.comparingInt(Match::score).reversed()
					.thenComparing(match -> match.definition().name()));

			JsonObject output = new JsonObject();
			output.addProperty("query", query);
			output.addProperty("kind", kind);
			output.addProperty("totalMatches", matches.size());
			JsonArray encoded = new JsonArray();
			matches.stream().limit(limit)
					.map(Match::definition)
					.map(DynamicContentAiDescription::summarize)
					.forEach(encoded::add);
			output.add("matches", encoded);
			output.addProperty("moreAvailable", matches.size() > limit);
			output.addProperty("message", matches.isEmpty()
					? "No generated content matched. Try a shorter query or kind=any."
					: "Use inspect_dynamic_content with an exact contentId for the complete persisted definition and Java source.");
			return ContentGenerationDraft.ToolExecution.success(output, null);
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("DYNAMIC_CONTENT_SEARCH_FAILED", safeMessage(error));
		}
	}

	static ContentGenerationDraft.ToolExecution inspect(JsonObject arguments) {
		try {
			requireOnly(arguments, "contentId");
			String contentId = requiredString(arguments, "contentId").strip().toLowerCase(Locale.ROOT);
			String name = contentId;
			int separator = contentId.indexOf(':');
			if (separator >= 0) {
				if (!contentId.substring(0, separator).equals(LittleChemistry.MOD_ID)) {
					throw new IllegalArgumentException("contentId namespace must be " + LittleChemistry.MOD_ID);
				}
				name = contentId.substring(separator + 1);
			}
			if (!name.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("contentId is invalid");

			DynamicContentDefinition definition = DynamicContentCatalog.find(name);
			if (definition == null) {
				return ContentGenerationDraft.ToolExecution.error("DYNAMIC_CONTENT_NOT_FOUND",
						"No generated content exists with ID " + LittleChemistry.MOD_ID + ":" + name
								+ ". Use search_dynamic_content first.");
			}

			JsonObject output = new JsonObject();
			output.addProperty("contentId", LittleChemistry.MOD_ID + ":" + definition.name());
			output.add("definition", DynamicContentJson.encodeDefinition(definition));
			output.add("implementedCallbacks",
					DynamicContentAiDescription.summarize(definition).getAsJsonObject("behavior")
							.getAsJsonArray("implementedCallbacks").deepCopy());
			output.addProperty("message", "This is the complete persisted definition, including untruncated behaviorSource and all stored texture/model data.");
			return ContentGenerationDraft.ToolExecution.success(output, null);
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("DYNAMIC_CONTENT_INSPECTION_FAILED", safeMessage(error));
		}
	}

	private static int score(DynamicContentDefinition definition, String query, List<String> tokens) {
		if (query.isEmpty()) return 0;
		String name = definition.name().toLowerCase(Locale.ROOT);
		String displayName = definition.displayName().toLowerCase(Locale.ROOT);
		String contentId = LittleChemistry.MOD_ID + ":" + name;
		String document = searchDocument(definition);
		for (String token : tokens) {
			if (!token.isBlank() && !document.contains(token)) return -1;
		}
		if (name.equals(query) || displayName.equals(query) || contentId.equals(query)) return 10_000;
		if (name.startsWith(query) || displayName.startsWith(query)) return 5_000;
		if (name.contains(query) || displayName.contains(query)) return 2_500;
		return document.contains(query) ? 1_000 : 100;
	}

	private static String searchDocument(DynamicContentDefinition definition) {
		StringBuilder document = new StringBuilder()
				.append(LittleChemistry.MOD_ID).append(':').append(definition.name()).append(' ')
				.append(definition.name()).append(' ')
				.append(definition.displayName()).append(' ')
				.append(definition.description()).append(' ')
				.append(definition.type().serializedName()).append(' ')
				.append(definition.rarityTier().serializedName()).append(' ');
		if (definition.block() != null) document.append(definition.block()).append(' ');
		if (definition.item() != null) document.append(definition.item()).append(' ');
		if (definition.armor() != null) document.append(definition.armor()).append(' ');
		if (definition.entity() != null) document.append(definition.entity()).append(' ');
		if (definition.blockModel() != null) document.append(definition.blockModel()).append(' ');
		if (definition.entityModel() != null) document.append(definition.entityModel()).append(' ');
		if (!definition.customParticles().isEmpty()) document.append(definition.customParticles()).append(' ');
		if (definition.workstation() != null) document.append(definition.workstation()).append(' ');
		document.append(definition.behaviorSource());
		return document.toString().toLowerCase(Locale.ROOT);
	}

	private static void requireOnly(JsonObject object, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String key : object.keySet()) {
			if (!names.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
		}
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			throw new IllegalArgumentException("Missing string: " + key);
		}
		return object.get(key).getAsString();
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		return message.replaceAll("[\\r\\n]+", " ").trim();
	}

	private record Match(DynamicContentDefinition definition, int score) {
	}
}
