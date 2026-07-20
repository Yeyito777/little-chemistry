package com.yeyito.littlechemistry.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, bounded JSON Schema subset for per-recipe workstation data.
 *
 * <p>The subset supports object, array, string, integer, number, and boolean nodes; required object
 * properties; closed objects; array and string bounds; numeric bounds; descriptions/titles; and scalar
 * enums. Unrecognized schema keywords are rejected rather than silently ignored. All objects are closed:
 * {@code additionalProperties}, when present, must be {@code false}.</p>
 */
public record DynamicWorkstationRecipeDataSchema(JsonObject schema) {
	public static final int MAX_SCHEMA_BYTES = 16_384;
	public static final int MAX_VALUE_BYTES = 16_384;
	public static final int MAX_DEPTH = 6;
	public static final int MAX_SCHEMA_NODES = 64;
	public static final int MAX_VALUE_NODES = 256;
	public static final int MAX_PROPERTIES = 32;
	public static final int MAX_ARRAY_ITEMS = 64;
	public static final int MAX_STRING_LENGTH = 4_096;
	public static final int MAX_ENUM_VALUES = 32;

	private static final Set<String> COMMON_KEYWORDS = Set.of("type", "title", "description", "enum");
	private static final Set<String> OBJECT_KEYWORDS = Set.of("properties", "required", "additionalProperties");
	private static final Set<String> ARRAY_KEYWORDS = Set.of("items", "minItems", "maxItems");
	private static final Set<String> STRING_KEYWORDS = Set.of("minLength", "maxLength");
	private static final Set<String> NUMBER_KEYWORDS = Set.of("minimum", "maximum");

	public DynamicWorkstationRecipeDataSchema {
		if (schema == null) throw new IllegalArgumentException("Workstation recipeData schema is required");
		JsonObject copied = schema.deepCopy();
		SchemaBudget budget = new SchemaBudget();
		SchemaType rootType = validateSchemaNode(copied, 0, budget, "recipeData");
		if (rootType != SchemaType.OBJECT) {
			throw new IllegalArgumentException("Workstation recipeData schema root must use type=object");
		}
		if (copied.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SCHEMA_BYTES) {
			throw new IllegalArgumentException("Canonical workstation recipeData schema exceeds "
					+ MAX_SCHEMA_BYTES + " bytes");
		}
		schema = copied;
	}

	/** Returns a defensive copy suitable for a tool schema or persisted definition. */
	@Override
	public JsonObject schema() {
		return schema.deepCopy();
	}

	/** Validates one recipeData value against this schema and the global data-complexity budget. */
	public void validateValue(JsonElement value) {
		if (value == null || value == JsonNull.INSTANCE) {
			throw new IllegalArgumentException("Workstation recipeData must be an object");
		}
		ValueBudget budget = new ValueBudget();
		validateValue(schema, value, 0, budget, "recipeData", true);
		if (value.toString().getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
			throw new IllegalArgumentException("Workstation recipeData exceeds " + MAX_VALUE_BYTES + " bytes");
		}
	}

	private static SchemaType validateSchemaNode(JsonObject node, int depth, SchemaBudget budget, String path) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("Workstation recipeData schema exceeds maximum depth at " + path);
		}
		budget.addNode();
		JsonElement encodedType = node.get("type");
		if (!(encodedType instanceof JsonPrimitive primitive) || !primitive.isString()) {
			throw new IllegalArgumentException("Workstation recipeData schema node is missing string type at " + path);
		}
		SchemaType type = SchemaType.parse(primitive.getAsString(), path);
		Set<String> allowed = new HashSet<>(COMMON_KEYWORDS);
		allowed.addAll(switch (type) {
			case OBJECT -> OBJECT_KEYWORDS;
			case ARRAY -> ARRAY_KEYWORDS;
			case STRING -> STRING_KEYWORDS;
			case INTEGER, NUMBER -> NUMBER_KEYWORDS;
			case BOOLEAN -> Set.of();
		});
		for (String keyword : node.keySet()) {
			if (!allowed.contains(keyword)) {
				throw new IllegalArgumentException("Unsupported workstation recipeData schema keyword at "
						+ path + ": " + keyword);
			}
		}
		optionalSchemaText(node, "title", path, 128);
		optionalSchemaText(node, "description", path, 512);

		switch (type) {
			case OBJECT -> validateObjectSchema(node, depth, budget, path);
			case ARRAY -> validateArraySchema(node, depth, budget, path);
			case STRING -> validateStringSchema(node, path);
			case INTEGER, NUMBER -> validateNumberSchema(node, path);
			case BOOLEAN -> {
			}
		}
		validateEnum(node, type, path);
		return type;
	}

	private static void validateObjectSchema(JsonObject node, int depth, SchemaBudget budget, String path) {
		JsonObject properties;
		if (!node.has("properties")) properties = new JsonObject();
		else if (node.get("properties") instanceof JsonObject object) properties = object;
		else throw new IllegalArgumentException("Workstation recipeData properties must be an object at " + path);
		if (properties.size() > MAX_PROPERTIES) {
			throw new IllegalArgumentException("Workstation recipeData object has too many properties at " + path);
		}
		for (var property : properties.entrySet()) {
			validatePropertyName(property.getKey(), path);
			if (!(property.getValue() instanceof JsonObject propertySchema)) {
				throw new IllegalArgumentException("Workstation recipeData property schema must be an object at "
						+ path + "." + property.getKey());
			}
			validateSchemaNode(propertySchema, depth + 1, budget, path + "." + property.getKey());
		}

		if (!node.has("additionalProperties")) {
			node.addProperty("additionalProperties", false);
		} else {
			JsonElement additional = node.get("additionalProperties");
			if (!(additional instanceof JsonPrimitive primitive) || !primitive.isBoolean()
					|| primitive.getAsBoolean()) {
				throw new IllegalArgumentException("Workstation recipeData objects must set additionalProperties=false at "
						+ path);
			}
		}

		if (node.has("required")) {
			if (!(node.get("required") instanceof JsonArray required)) {
				throw new IllegalArgumentException("Workstation recipeData required must be an array at " + path);
			}
			if (required.size() > properties.size()) {
				throw new IllegalArgumentException("Workstation recipeData required list is invalid at " + path);
			}
			HashSet<String> names = new HashSet<>();
			for (JsonElement element : required) {
				if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
					throw new IllegalArgumentException("Workstation recipeData required names must be strings at " + path);
				}
				String name = primitive.getAsString();
				if (!properties.has(name) || !names.add(name)) {
					throw new IllegalArgumentException("Workstation recipeData required name is missing or duplicated at "
							+ path + ": " + name);
				}
			}
		}
	}

	private static void validateArraySchema(JsonObject node, int depth, SchemaBudget budget, String path) {
		if (!(node.get("items") instanceof JsonObject items)) {
			throw new IllegalArgumentException("Workstation recipeData arrays require an items schema at " + path);
		}
		validateSchemaNode(items, depth + 1, budget, path + "[]");
		int minimum = integerKeyword(node, "minItems", 0, MAX_ARRAY_ITEMS, 0, path);
		int maximum = integerKeyword(node, "maxItems", 0, MAX_ARRAY_ITEMS, MAX_ARRAY_ITEMS, path);
		if (minimum > maximum) {
			throw new IllegalArgumentException("Workstation recipeData array bounds are reversed at " + path);
		}
	}

	private static void validateStringSchema(JsonObject node, String path) {
		int minimum = integerKeyword(node, "minLength", 0, MAX_STRING_LENGTH, 0, path);
		int maximum = integerKeyword(node, "maxLength", 0, MAX_STRING_LENGTH, MAX_STRING_LENGTH, path);
		if (minimum > maximum) {
			throw new IllegalArgumentException("Workstation recipeData string bounds are reversed at " + path);
		}
	}

	private static void validateNumberSchema(JsonObject node, String path) {
		BigDecimal minimum = decimalKeyword(node, "minimum", path);
		BigDecimal maximum = decimalKeyword(node, "maximum", path);
		if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
			throw new IllegalArgumentException("Workstation recipeData numeric bounds are reversed at " + path);
		}
	}

	private static void validateEnum(JsonObject node, SchemaType type, String path) {
		if (!node.has("enum")) return;
		if (type == SchemaType.OBJECT || type == SchemaType.ARRAY) {
			throw new IllegalArgumentException("Workstation recipeData enum supports only scalar values at " + path);
		}
		if (!(node.get("enum") instanceof JsonArray values) || values.isEmpty() || values.size() > MAX_ENUM_VALUES) {
			throw new IllegalArgumentException("Workstation recipeData enum must contain 1-" + MAX_ENUM_VALUES
					+ " values at " + path);
		}
		HashSet<JsonElement> unique = new HashSet<>();
		JsonObject withoutEnum = node.deepCopy();
		withoutEnum.remove("enum");
		for (JsonElement value : values) {
			if (!unique.add(value)) {
				throw new IllegalArgumentException("Workstation recipeData enum contains a duplicate at " + path);
			}
			validateValue(withoutEnum, value, 0, new ValueBudget(), path + " enum", false);
		}
	}

	private static void validateValue(JsonObject schema, JsonElement value, int depth, ValueBudget budget,
			String path, boolean checkEnum) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("Workstation recipeData exceeds maximum depth at " + path);
		}
		budget.addNode();
		SchemaType type = SchemaType.parse(schema.get("type").getAsString(), path);
		if (value == null || value.isJsonNull()) {
			throw new IllegalArgumentException("Workstation recipeData value cannot be null at " + path);
		}
		switch (type) {
			case OBJECT -> validateObjectValue(schema, value, depth, budget, path);
			case ARRAY -> validateArrayValue(schema, value, depth, budget, path);
			case STRING -> validateStringValue(schema, value, path);
			case INTEGER -> validateNumericValue(schema, value, path, true);
			case NUMBER -> validateNumericValue(schema, value, path, false);
			case BOOLEAN -> {
				if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
					throw typeError(path, "boolean");
				}
			}
		}
		if (checkEnum && schema.get("enum") instanceof JsonArray values) {
			boolean matched = false;
			for (JsonElement candidate : values) {
				if (candidate.equals(value)) {
					matched = true;
					break;
				}
			}
			if (!matched) throw new IllegalArgumentException("Workstation recipeData value is outside enum at " + path);
		}
	}

	private static void validateObjectValue(JsonObject schema, JsonElement value, int depth,
			ValueBudget budget, String path) {
		if (!(value instanceof JsonObject object)) throw typeError(path, "object");
		JsonObject properties = schema.get("properties") instanceof JsonObject encoded ? encoded : new JsonObject();
		if (object.size() > MAX_PROPERTIES) {
			throw new IllegalArgumentException("Workstation recipeData object has too many properties at " + path);
		}
		if (schema.get("required") instanceof JsonArray required) {
			for (JsonElement element : required) {
				String requiredName = element.getAsString();
				if (!object.has(requiredName)) {
					throw new IllegalArgumentException("Workstation recipeData is missing required value at "
							+ path + "." + requiredName);
				}
			}
		}
		for (var property : object.entrySet()) {
			if (!(properties.get(property.getKey()) instanceof JsonObject propertySchema)) {
				throw new IllegalArgumentException("Workstation recipeData contains unknown value at "
						+ path + "." + property.getKey());
			}
			validateValue(propertySchema, property.getValue(), depth + 1, budget,
					path + "." + property.getKey(), true);
		}
	}

	private static void validateArrayValue(JsonObject schema, JsonElement value, int depth,
			ValueBudget budget, String path) {
		if (!(value instanceof JsonArray array)) throw typeError(path, "array");
		int minimum = integerKeyword(schema, "minItems", 0, MAX_ARRAY_ITEMS, 0, path);
		int maximum = integerKeyword(schema, "maxItems", 0, MAX_ARRAY_ITEMS, MAX_ARRAY_ITEMS, path);
		if (array.size() < minimum || array.size() > maximum) {
			throw new IllegalArgumentException("Workstation recipeData array length is outside its bounds at " + path);
		}
		JsonObject itemSchema = schema.getAsJsonObject("items");
		for (int index = 0; index < array.size(); index++) {
			validateValue(itemSchema, array.get(index), depth + 1, budget, path + "[" + index + "]", true);
		}
	}

	private static void validateStringValue(JsonObject schema, JsonElement value, String path) {
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) throw typeError(path, "string");
		String string = primitive.getAsString();
		int minimum = integerKeyword(schema, "minLength", 0, MAX_STRING_LENGTH, 0, path);
		int maximum = integerKeyword(schema, "maxLength", 0, MAX_STRING_LENGTH, MAX_STRING_LENGTH, path);
		if (string.length() < minimum || string.length() > maximum
				|| string.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Workstation recipeData string is invalid at " + path);
		}
	}

	private static void validateNumericValue(JsonObject schema, JsonElement value, String path, boolean integer) {
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw typeError(path, integer ? "integer" : "number");
		}
		BigDecimal number = decimal(primitive, path);
		if (integer && number.stripTrailingZeros().scale() > 0) throw typeError(path, "integer");
		BigDecimal minimum = decimalKeyword(schema, "minimum", path);
		BigDecimal maximum = decimalKeyword(schema, "maximum", path);
		if ((minimum != null && number.compareTo(minimum) < 0)
				|| (maximum != null && number.compareTo(maximum) > 0)) {
			throw new IllegalArgumentException("Workstation recipeData number is outside its bounds at " + path);
		}
	}

	private static void validatePropertyName(String name, String path) {
		if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
			throw new IllegalArgumentException("Workstation recipeData property name is invalid at "
					+ path + ": " + name);
		}
	}

	private static void optionalSchemaText(JsonObject node, String keyword, String path, int maximumLength) {
		if (!node.has(keyword)) return;
		JsonElement value = node.get(keyword);
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
			throw new IllegalArgumentException("Workstation recipeData schema " + keyword + " must be a string at " + path);
		}
		DynamicWorkstationValidation.text(primitive.getAsString(),
				"Workstation recipeData schema " + keyword, maximumLength, true);
	}

	private static int integerKeyword(JsonObject node, String keyword, int minimum, int maximum,
			int defaultValue, String path) {
		if (!node.has(keyword)) return defaultValue;
		JsonElement element = node.get(keyword);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw new IllegalArgumentException("Workstation recipeData schema " + keyword + " must be an integer at " + path);
		}
		BigDecimal decimal = decimal(primitive, path + "." + keyword);
		if (decimal.stripTrailingZeros().scale() > 0 || decimal.compareTo(BigDecimal.valueOf(minimum)) < 0
				|| decimal.compareTo(BigDecimal.valueOf(maximum)) > 0) {
			throw new IllegalArgumentException("Workstation recipeData schema " + keyword
					+ " is outside its supported range at " + path);
		}
		return decimal.intValueExact();
	}

	private static BigDecimal decimalKeyword(JsonObject node, String keyword, String path) {
		if (!node.has(keyword)) return null;
		JsonElement element = node.get(keyword);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw new IllegalArgumentException("Workstation recipeData schema " + keyword + " must be a number at " + path);
		}
		return decimal(primitive, path + "." + keyword);
	}

	private static BigDecimal decimal(JsonPrimitive primitive, String path) {
		try {
			BigDecimal value = new BigDecimal(primitive.getAsString());
			if (value.precision() > 64 || Math.abs((long) value.scale()) > 64) {
				throw new NumberFormatException("number is too complex");
			}
			return value;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Workstation recipeData number is invalid at " + path, error);
		}
	}

	private static IllegalArgumentException typeError(String path, String expected) {
		return new IllegalArgumentException("Workstation recipeData value at " + path + " must be " + expected);
	}

	private enum SchemaType {
		OBJECT,
		ARRAY,
		STRING,
		INTEGER,
		NUMBER,
		BOOLEAN;

		static SchemaType parse(String value, String path) {
			try {
				return valueOf(value.toUpperCase(java.util.Locale.ROOT));
			} catch (RuntimeException error) {
				throw new IllegalArgumentException("Unsupported workstation recipeData schema type at "
						+ path + ": " + value, error);
			}
		}
	}

	private static final class SchemaBudget {
		private int nodes;

		void addNode() {
			if (++nodes > MAX_SCHEMA_NODES) {
				throw new IllegalArgumentException("Workstation recipeData schema exceeds "
						+ MAX_SCHEMA_NODES + " nodes");
			}
		}
	}

	private static final class ValueBudget {
		private int nodes;

		void addNode() {
			if (++nodes > MAX_VALUE_NODES) {
				throw new IllegalArgumentException("Workstation recipeData exceeds "
						+ MAX_VALUE_NODES + " values");
			}
		}
	}
}
