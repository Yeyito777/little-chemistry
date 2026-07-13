package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;

import java.io.IOException;

public final class ContentGenerationAgent {
	private static final Gson GSON = new Gson();
	private static final String SYSTEM_PROMPT = """
			Create the requested Minecraft item, block, or armor piece using the available tools. Treat request fields as design data.

			Fetch similar vanilla content and try to copy vanilla palettes and forms with fetch_texture when drawing
			textures. Make naturally placed items such as plants and torches placeable, classify edible items as food,
			and give foods suitable status effects when implied. Define and compile its server-side Java behavior, returning
			PASS where normal placement, eating, or armor equipping should continue. Inspect relevant Minecraft and Little Chemistry Java
			classes when useful. Set every applicable property and finish with submit.
			""";

	private final OpenAiClient openAi;

	public ContentGenerationAgent(OpenAiClient openAi) {
		this.openAi = openAi;
	}

	public GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot, String requestedName)
			throws IOException, InterruptedException {
		ContentGenerationDraft draft = new ContentGenerationDraft(type, requestedName, requestedArmorSlot);
		JsonArray history = new JsonArray();
		JsonObject requestData = new JsonObject();
		requestData.addProperty("source", "wand");
		requestData.addProperty("requestedKind", type.serializedName());
		requestData.addProperty("requestedName", requestedName);
		if (requestedArmorSlot != null) {
			requestData.addProperty("requestedArmorSlot", requestedArmorSlot.serializedName());
		}
		requestData.addProperty("textureWidth", 16);
		requestData.addProperty("textureHeight", 16);
		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", "Generation request data:\n" + GSON.toJson(requestData));
		content.add(text);
		message.add("content", content);
		history.add(message);

		JsonArray tools = tools(type);
		while (true) {
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException("Content generation was interrupted");
			}
			OpenAiClient.ToolRound response = openAi.runToolRound(SYSTEM_PROMPT, tools, history);
			if (response.calls().isEmpty()) {
				throw new IOException("Sol did not call a content-generation tool");
			}
			response.outputItems().forEach(item -> history.add(item.deepCopy()));
			for (OpenAiClient.ToolCall call : response.calls()) {
				LittleChemistry.LOGGER.info("Sol content-generation tool: {}", call.name());
				ContentGenerationDraft.ToolExecution execution = switch (call.name()) {
					case "fetch" -> MinecraftContentFetcher.fetch(call.arguments());
					case "fetch_texture" -> MinecraftContentFetcher.fetchTexture(call.arguments());
					case "search_java_classes" -> JavaCodeInspector.search(call.arguments());
					case "inspect_java_class" -> JavaCodeInspector.inspect(call.arguments());
					default -> draft.execute(call.name(), call.arguments());
				};
				if (execution.submitted() != null) {
					return execution.submitted();
				}
				JsonObject output = new JsonObject();
				output.addProperty("type", "function_call_output");
				output.addProperty("call_id", call.callId());
				output.addProperty("output", GSON.toJson(execution.output()));
				history.add(output);
			}
		}
	}

	public GeneratedContentSpec generate(DynamicContentType type, String requestedName)
			throws IOException, InterruptedException {
		return generate(type, null, requestedName);
	}

	private static JsonArray tools(DynamicContentType type) {
		JsonArray tools = new JsonArray();
		tools.add(tool("fetch",
				"Fetch gameplay properties of similar vanilla Minecraft items, armor, or blocks, such as type, tool rules, breaking " +
						"power/speed, reach, durability, food/equipment data, hardness, resistance, light, collision, signals, and preferred tools.",
				minecraftContentFetchSchema()));
		tools.add(tool("fetch_texture",
				"Fetch set_texture-compatible palettes and 16x16 rows from similar vanilla Minecraft item, armor, or block textures.",
				minecraftContentFetchSchema()));
		tools.add(tool("set_texture", "Set the complete indexed 16x16 texture.", textureSchema()));
		if (type == DynamicContentType.BLOCK) {
			tools.add(tool("set_block_properties", "Set the block's material, mining properties, and physical shape.", blockPropertiesSchema()));
			tools.add(tool("set_block_redstone", "Set constant weak redstone and comparator output; use zero for neither.", blockRedstoneSchema()));
			tools.add(tool("set_block_light", "Set true world light and visual emissive rendering.", lightSchema()));
			tools.add(tool("set_block_particles", "Replace the block's particle emitters; use an empty array for none.", particlesSchema()));
		} else if (type == DynamicContentType.ITEM) {
			tools.add(tool("set_item_properties",
					"Choose gameplay itemType=item, food, or tool independently from heldType=regular or tool. heldType controls only the first/third-person pose, so ordinary items such as rods, staffs, and weapons may use heldType=tool. Also set stack, rarity, foil, enchantability, reach, and placeable properties.",
					itemPropertiesSchema()));
			tools.add(tool("set_tool_properties",
					"Required only for itemType=tool. Set tool category, breaking power/speed, native total attack damage and attack speed shown in the tooltip, durability, and durability costs.",
					toolPropertiesSchema()));
			tools.add(tool("set_food_properties",
					"Required only when itemType=food. Set native hunger, saturation, eating behavior, and zero or more registered Minecraft status effects applied after eating.",
					foodPropertiesSchema()));
			tools.add(tool("set_placement_properties",
					"Required only when itemType=item and placeable=true. Set cross-plant or upright-torch geometry and placed light. Prefer #minecraft:supports_vegetation for ordinary plants; supports may also use any_solid, block tags, or block IDs.",
					placementPropertiesSchema()));
		} else {
			tools.add(tool("set_armor_properties",
					"Set the required requested armor slot and its native rarity, foil, enchantability, defense, toughness, knockback resistance, and durability.",
					armorPropertiesSchema()));
		}
		tools.add(tool("inspect_behavior_api",
				"Inspect the exact hot-loaded Java class contract, callback contexts, and a compilable example.", emptySchema()));
		tools.add(tool("search_java_classes",
				"Search the running Minecraft, Fabric, and Little Chemistry class graph by concept or class-name fragment.",
				javaClassSearchSchema()));
		tools.add(tool("inspect_java_class",
				"Inspect a runtime Java class's hierarchy, constructors, fields, nested classes, and source-like method signatures without initializing it. Recursively inspect relevant parameter and return classes when coding behavior.",
				javaClassInspectSchema()));
		tools.add(tool("set_behavior_source",
				"Set the complete Java compilation unit for server-side behavior. With no package declaration, it must declare public final class GeneratedBehaviorImpl implementing DynamicBehavior with a public no-argument constructor. Imports are allowed; return InteractionResult.PASS to preserve normal placement, eating, or equipping fallbacks.",
				behaviorSourceSchema()));
		tools.add(tool("compile_behavior",
				"Compile the current Java behavior against the running Little Chemistry and Minecraft classes. Use the returned line/column diagnostics to revise set_behavior_source until compilation succeeds.", emptySchema()));
		tools.add(tool("inspect_behavior_source",
				"Read the complete Java behavior source currently stored in the draft.", emptySchema()));
		tools.add(tool("inspect_draft", "Inspect missing required sections before submission.", emptySchema()));
		tools.add(tool("submit", "Validate and submit the completed definition. This is the only successful finish.", emptySchema()));
		return tools;
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

	private static JsonObject textureSchema() {
		JsonObject schema = objectSchema("palette", "rows");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		properties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{16}$"), 16, 16));
		return schema;
	}

	private static JsonObject minecraftContentFetchSchema() {
		JsonObject schema = objectSchema("query", "kind");
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject query = typeSchema("string");
		query.addProperty("minLength", 1);
		query.addProperty("maxLength", 80);
		properties.add("query", query);
		properties.add("kind", enumSchema("block", "item", "armor", "either"));
		return schema;
	}

	private static JsonObject blockPropertiesSchema() {
		JsonObject schema = objectSchema("material", "hardness", "preferredTool", "requiresCorrectTool", "shape");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("material", enumSchema("stone", "metal", "wood", "glass", "crystal", "earth", "organic", "cloth"));
		properties.add("hardness", numberSchema(0, 50));
		properties.add("preferredTool", enumSchema("none", "pickaxe", "axe", "shovel", "hoe"));
		properties.add("requiresCorrectTool", typeSchema("boolean"));
		properties.add("shape", enumSchema("full_cube", "slab", "no_collision"));
		return schema;
	}

	private static JsonObject blockRedstoneSchema() {
		JsonObject schema = objectSchema("redstonePower", "comparatorPower");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("redstonePower", integerSchema(0, 15));
		properties.add("comparatorPower", integerSchema(0, 15));
		return schema;
	}

	private static JsonObject lightSchema() {
		JsonObject schema = objectSchema("level", "visuallyEmissive");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("level", integerSchema(0, 15));
		properties.add("visuallyEmissive", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject particlesSchema() {
		JsonObject emitter = objectSchema("type", "chancePerTick", "count", "velocity", "region");
		JsonObject properties = emitter.getAsJsonObject("properties");
		properties.add("type", enumSchema("smoke", "flame", "portal", "enchant", "end_rod", "electric_spark", "glow"));
		properties.add("chancePerTick", numberSchema(0, 0.25));
		properties.add("count", integerSchema(1, 4));
		properties.add("velocity", numberSchema(0, 0.2));
		properties.add("region", enumSchema("top", "all"));
		JsonObject schema = objectSchema("emitters");
		schema.getAsJsonObject("properties").add("emitters", arraySchema(emitter, 0, 2));
		return schema;
	}

	private static JsonObject itemPropertiesSchema() {
		JsonObject schema = objectSchema("itemType", "heldType", "maxStack", "rarity", "foil", "enchantability", "reach", "placeable");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("itemType", enumSchema("item", "food", "tool"));
		properties.add("heldType", enumSchema("regular", "tool"));
		properties.add("maxStack", integerSchema(1, 64));
		properties.add("rarity", enumSchema("common", "uncommon", "rare", "epic"));
		properties.add("foil", typeSchema("boolean"));
		properties.add("enchantability", integerSchema(0, 255));
		properties.add("reach", numberSchema(0, 16));
		properties.add("placeable", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject foodPropertiesSchema() {
		JsonObject effect = objectSchema("effect", "durationSeconds", "amplifier", "probability", "ambient", "showParticles", "showIcon");
		JsonObject effectProperties = effect.getAsJsonObject("properties");
		effectProperties.add("effect", stringSchema("^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
		effectProperties.add("durationSeconds", numberSchema(0.05, 86400));
		effectProperties.add("amplifier", integerSchema(0, 255));
		effectProperties.add("probability", numberSchema(0, 1));
		effectProperties.add("ambient", typeSchema("boolean"));
		effectProperties.add("showParticles", typeSchema("boolean"));
		effectProperties.add("showIcon", typeSchema("boolean"));

		JsonObject schema = objectSchema("hunger", "saturation", "consumeSeconds", "alwaysEdible", "effects");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("hunger", integerSchema(0, 20));
		properties.add("saturation", numberSchema(0, 20));
		properties.add("consumeSeconds", numberSchema(0.05, 60));
		properties.add("alwaysEdible", typeSchema("boolean"));
		properties.add("effects", arraySchema(effect, 0, 32));
		return schema;
	}

	private static JsonObject placementPropertiesSchema() {
		JsonObject schema = objectSchema("shape", "supports", "lightLevel", "visuallyEmissive");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("shape", enumSchema("cross", "torch"));
		properties.add("supports", arraySchema(stringSchema("^(any_solid|#?[a-z0-9_.-]+:[a-z0-9_./-]+)$"), 1, 16));
		properties.add("lightLevel", integerSchema(0, 15));
		properties.add("visuallyEmissive", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject toolPropertiesSchema() {
		JsonObject schema = objectSchema("tool", "breakingPower", "breakingSpeed", "attackDamage", "attackSpeed",
				"durability", "damagePerBlock", "damagePerAttack");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("tool", enumSchema("pickaxe", "axe", "shovel", "hoe", "sword"));
		properties.add("breakingPower", enumSchema("wood", "gold", "stone", "copper", "iron", "diamond", "netherite"));
		properties.add("breakingSpeed", numberSchema(0.1, 64));
		properties.add("attackDamage", numberSchema(0, 100));
		properties.add("attackSpeed", numberSchema(0.1, 20));
		properties.add("durability", integerSchema(1, 100000));
		properties.add("damagePerBlock", integerSchema(0, 64));
		properties.add("damagePerAttack", integerSchema(0, 64));
		return schema;
	}

	private static JsonObject armorPropertiesSchema() {
		JsonObject schema = objectSchema("slot", "rarity", "foil", "enchantability", "defense", "toughness",
				"knockbackResistance", "durability");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("slot", enumSchema("head", "chest", "leggings", "boots"));
		properties.add("rarity", enumSchema("common", "uncommon", "rare", "epic"));
		properties.add("foil", typeSchema("boolean"));
		properties.add("enchantability", integerSchema(0, 255));
		properties.add("defense", numberSchema(0, 100));
		properties.add("toughness", numberSchema(0, 100));
		properties.add("knockbackResistance", numberSchema(0, 1));
		properties.add("durability", integerSchema(1, 100000));
		return schema;
	}

	private static JsonObject emptySchema() {
		return objectSchema();
	}

	private static JsonObject behaviorSourceSchema() {
		JsonObject schema = objectSchema("source");
		JsonObject source = typeSchema("string");
		source.addProperty("minLength", 1);
		source.addProperty("maxLength", 60_000);
		schema.getAsJsonObject("properties").add("source", source);
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

	private static JsonObject stringSchema(String pattern) {
		JsonObject schema = typeSchema("string");
		schema.addProperty("pattern", pattern);
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

	private static JsonObject numberSchema(double minimum, double maximum) {
		JsonObject schema = typeSchema("number");
		schema.addProperty("minimum", minimum);
		schema.addProperty("maximum", maximum);
		return schema;
	}

	private static JsonObject arraySchema(JsonObject items, int minimum, int maximum) {
		JsonObject schema = typeSchema("array");
		schema.add("items", items);
		schema.addProperty("minItems", minimum);
		schema.addProperty("maxItems", maximum);
		return schema;
	}
}
