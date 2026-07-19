package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Shared read-only world-content and runtime-code inspection tools available to generation agents. */
final class GenerationInspectionTools {
	private GenerationInspectionTools() {
	}

	static void addTo(JsonArray tools) {
		tools.add(tool("search_dynamic_content",
				"Search all previously generated content in this world by name, description, type, gameplay properties, callback, or Java behavior concept. Results are summaries; use inspect_dynamic_content for complete source and visual data.",
				dynamicContentSearchSchema()));
		tools.add(tool("inspect_dynamic_content",
				"Inspect one previously generated item, block, or armor definition in full, including untruncated Java behavior source and every stored texture/model/property field.",
				dynamicContentInspectSchema()));
		tools.add(tool("search_java_classes",
				"Search the running Minecraft, Fabric, and Little Chemistry class graph by concept or class-name fragment.",
				javaClassSearchSchema()));
		tools.add(tool("inspect_java_class",
				"Inspect a runtime Java class's hierarchy, constructors, fields, nested classes, and method signatures without initializing it.",
				javaClassInspectSchema()));
		tools.add(tool("inspect_java_source",
				"Decompile the installed class-file family of an exact Minecraft, Fabric, or Little Chemistry class into source-like Java, including method bodies. Use search_java_classes and inspect_java_class first to choose a precise class.",
				javaClassSourceSchema()));
	}

	static ContentGenerationDraft.ToolExecution execute(String name, JsonObject arguments) {
		return switch (name) {
			case "search_dynamic_content" -> DynamicContentInspector.search(arguments);
			case "inspect_dynamic_content" -> DynamicContentInspector.inspect(arguments);
			case "search_java_classes" -> JavaCodeInspector.search(arguments);
			case "inspect_java_class" -> JavaCodeInspector.inspect(arguments);
			case "inspect_java_source" -> JavaCodeInspector.inspectSource(arguments);
			default -> null;
		};
	}

	private static JsonObject dynamicContentSearchSchema() {
		JsonObject schema = objectSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject query = typeSchema("string");
		query.addProperty("maxLength", 160);
		properties.add("query", query);
		properties.add("kind", enumSchema("any", "item", "block", "armor"));
		properties.add("limit", integerSchema(1, 100));
		return schema;
	}

	private static JsonObject dynamicContentInspectSchema() {
		JsonObject schema = objectSchema("contentId");
		JsonObject contentId = typeSchema("string");
		contentId.addProperty("pattern", "^(little_chemistry:)?[a-z0-9_]{1,64}$");
		schema.getAsJsonObject("properties").add("contentId", contentId);
		return schema;
	}

	private static JsonObject javaClassSearchSchema() {
		JsonObject schema = objectSchema("query");
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject query = typeSchema("string");
		query.addProperty("minLength", 1);
		query.addProperty("maxLength", 120);
		properties.add("query", query);
		properties.add("scope", enumSchema("any", "minecraft", "little_chemistry", "fabric"));
		return schema;
	}

	private static JsonObject javaClassInspectSchema() {
		JsonObject schema = objectSchema("className");
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject className = typeSchema("string");
		className.addProperty("minLength", 1);
		className.addProperty("maxLength", 240);
		properties.add("className", className);
		JsonObject memberQuery = typeSchema("string");
		memberQuery.addProperty("maxLength", 120);
		properties.add("memberQuery", memberQuery);
		properties.add("includeInherited", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject javaClassSourceSchema() {
		JsonObject schema = objectSchema("className");
		JsonObject className = typeSchema("string");
		className.addProperty("minLength", 1);
		className.addProperty("maxLength", 240);
		schema.getAsJsonObject("properties").add("className", className);
		return schema;
	}

	private static JsonObject tool(String name, String description, JsonObject parameters) {
		JsonObject tool = new JsonObject();
		tool.addProperty("type", "function");
		tool.addProperty("name", name);
		tool.addProperty("description", description);
		tool.add("parameters", parameters);
		tool.addProperty("strict", false);
		return tool;
	}

	private static JsonObject objectSchema(String... required) {
		JsonObject schema = typeSchema("object");
		schema.add("properties", new JsonObject());
		schema.addProperty("additionalProperties", false);
		JsonArray requiredFields = new JsonArray();
		for (String field : required) requiredFields.add(field);
		schema.add("required", requiredFields);
		return schema;
	}

	private static JsonObject typeSchema(String type) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", type);
		return schema;
	}

	private static JsonObject enumSchema(String... values) {
		JsonObject schema = typeSchema("string");
		JsonArray encoded = new JsonArray();
		for (String value : values) encoded.add(value);
		schema.add("enum", encoded);
		return schema;
	}

	private static JsonObject integerSchema(int minimum, int maximum) {
		JsonObject schema = typeSchema("integer");
		schema.addProperty("minimum", minimum);
		schema.addProperty("maximum", maximum);
		return schema;
	}
}
