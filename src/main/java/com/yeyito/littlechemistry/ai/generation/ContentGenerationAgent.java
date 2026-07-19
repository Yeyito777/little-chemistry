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
			Create the requested Minecraft item, block, or armor piece with the tools. For crafting requests, derive a cohesive result
				from the grid, reflecting significant ingredients in its appearance, properties, and behavior. Fetch similar vanilla content
				for reference, but do not merely reskin or delegate to vanilla when the concept implies special functionality. Complete every
				applicable property and texture. When distinctive effects improve the concept, author reusable custom particles and emit them
				from block ambience or GeneratedBehaviorImpl. Author and compile GeneratedBehaviorImpl, inspect the finished draft, then submit.
			""";

	private final OpenAiClient openAi;

	public ContentGenerationAgent(OpenAiClient openAi) {
		this.openAi = openAi;
	}

	public GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot, String requestedName)
			throws IOException, InterruptedException {
		return generate(type, requestedArmorSlot, requestedName, 1, null);
	}

	public GeneratedContentSpec generateForRecipe(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, int requestedOutputCount, JsonObject craftingContext)
			throws IOException, InterruptedException {
		if (craftingContext == null) throw new IllegalArgumentException("craftingContext");
		return generate(type, requestedArmorSlot, requestedName, requestedOutputCount, craftingContext);
	}

	private GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, int requestedOutputCount, JsonObject craftingContext)
			throws IOException, InterruptedException {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				type, requestedName, requestedArmorSlot, requestedOutputCount);
		JsonArray history = new JsonArray();
		JsonObject requestData = new JsonObject();
		requestData.addProperty("source", craftingContext == null ? "wand" : "crafting");
		requestData.addProperty("requestedKind", type.serializedName());
		requestData.addProperty("requestedName", requestedName);
		if (craftingContext != null) requestData.addProperty("requestedOutputCount", requestedOutputCount);
		if (craftingContext != null) requestData.add("craftingGrid", craftingContext.deepCopy());
		if (requestedArmorSlot != null) {
			requestData.addProperty("requestedArmorSlot", requestedArmorSlot.serializedName());
		}
		if (type == DynamicContentType.BLOCK) {
			requestData.addProperty("blockTextureDimensions", "Each named model texture independently chooses a width and height from 1-64 pixels");
		} else {
			requestData.addProperty("textureWidth", 16);
			requestData.addProperty("textureHeight", 16);
		}
		if (type == DynamicContentType.ARMOR) {
			requestData.addProperty("armorDisplayTextureWidth", 64);
			requestData.addProperty("armorDisplayTextureHeight", 32);
		}
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

		JsonArray tools = tools(type, requestedArmorSlot);
		while (true) {
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException("Content generation was interrupted");
			}
			OpenAiClient.ToolRound response = openAi.runToolRound(SYSTEM_PROMPT, tools, history);
			if (response.calls().isEmpty()) {
				throw new IOException(openAi.model() + " did not call a content-generation tool");
			}
			response.outputItems().forEach(item -> history.add(item.deepCopy()));
			for (OpenAiClient.ToolCall call : response.calls()) {
				LittleChemistry.LOGGER.info("{} content-generation tool for {} '{}': {}",
						openAi.model(), type.serializedName(), requestedName, call.name());
				if (call.name().equals("set_placement_properties") && call.arguments().has("supportProfile")) {
					LittleChemistry.LOGGER.info("{} placement plan for item '{}': profile={}, supplied supports={}",
							openAi.model(), requestedName,
							call.arguments().get("supportProfile"),
							call.arguments().has("supports") ? call.arguments().get("supports") : "missing");
				}
				ContentGenerationDraft.ToolExecution execution = switch (call.name()) {
					case "fetch" -> MinecraftContentFetcher.fetch(call.arguments());
					case "fetch_texture" -> MinecraftContentFetcher.fetchTexture(call.arguments());
					case "fetch_armor_display_texture" -> MinecraftContentFetcher.fetchArmorDisplayTexture(call.arguments());
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

	private static JsonArray tools(DynamicContentType type, DynamicArmorSlot requestedArmorSlot) {
		JsonArray tools = new JsonArray();
		tools.add(tool("set_metadata",
				"Set the content rarity and a short one-sentence tooltip description. Choose by ascending tier: common (white) < uncommon (yellow) < rare (aqua) < epic (light purple) < legendary (gold) < mythical (red).",
				metadataSchema()));
		tools.add(tool("fetch",
				"Fetch gameplay properties of similar vanilla Minecraft items, armor, or blocks, such as type, tool rules, breaking " +
						"power/speed, reach, durability, food/equipment data, hardness, resistance, light, collision, signals, and preferred tools.",
				minecraftContentFetchSchema()));
		tools.add(tool("fetch_texture",
				type == DynamicContentType.BLOCK
						? "Fetch indexed 16x16 vanilla block-texture references. Use one or more as references in set_block_model; generated model textures may independently use other dimensions."
						: "Fetch set_texture-compatible palettes and 16x16 rows from similar vanilla Minecraft item icons or armor item icons.",
				minecraftContentFetchSchema()));
		tools.add(tool("set_custom_particles",
				"Replace the content's reusable AI-authored particle library with zero to four definitions. Each definition has 1-4 square indexed frames, linear size/color transitions, animation, and bounded physics. Block ambient emitters reference custom:<id>. Server-side Java behavior emits a declared string-literal local ID with com.yeyito.littlechemistry.particle.DynamicParticles.spawn.",
				customParticlesSchema()));
		if (type == DynamicContentType.BLOCK) {
			tools.add(tool("set_block_properties",
					"Choose gameplay properties and the visual/physical model category. Presets are full_cube, slab, no_collision, star, fence, cross, and torch. Use custom for an AI-authored model made from axis-aligned cuboids.",
					blockPropertiesSchema()));
			tools.add(tool("set_block_model",
					"Required after set_block_properties. Define 1-12 named indexed textures at any 1-64 pixel dimensions, choose the breaking-particle texture, and map each block face to a texture. Reuse a texture ID for a single-texture block or choose different top/bottom/sides. Omit UV to let preset/custom cuboid dimensions crop the texture like a vanilla model; supply UV in 0-16 model coordinates to override it. For shape=custom, define 1-24 axis-aligned cuboids with 0-16 bounds, collision control, and optional per-element face overrides; other shapes require an empty elements array.",
					blockModelSchema()));
			tools.add(tool("set_block_redstone", "Set constant weak redstone and comparator output; use zero for neither.", blockRedstoneSchema()));
			tools.add(tool("set_block_light", "Set true world light and visual emissive rendering.", lightSchema()));
			tools.add(tool("set_block_particles", "Replace the block's particle emitters; use an empty array for none. particle is one of smoke, flame, portal, enchant, end_rod, electric_spark, glow, or custom:<id> referencing set_custom_particles.", particlesSchema()));
		} else if (type == DynamicContentType.ITEM) {
			tools.add(tool("set_texture", "Set the complete indexed 16x16 inventory texture.", textureSchema()));
			tools.add(tool("set_item_properties",
					"Choose gameplay itemType independently from the visual heldType pose. Examples: regular for materials, food, and small objects; tool for swords, pickaxes, axes, staffs, and wands; rod for fishing rods or reversed long rods; bow for bows; crossbow for crossbows; mace for hammers, clubs, and other heavy-headed weapons; spear for spears and lances. A pose does not add its example's mechanics or animations. Also set stack, foil, enchantability, reach, placeable properties, and craftingUse. Use consume normally, keep for unchanged reusable catalysts or molds, and damage for reusable tools such as cutters or hammers that lose one durability per craft. maxStack must accommodate requestedOutputCount when supplied.",
					itemPropertiesSchema()));
			tools.add(tool("set_tool_properties",
					"Required only for itemType=tool. Set tool category, breaking power/speed, native total attack damage and attack speed shown in the tooltip, durability, and durability costs.",
					toolPropertiesSchema()));
			tools.add(tool("set_food_properties",
					"Required only when itemType=food. Set native hunger, saturation, eating behavior, and zero or more registered Minecraft status effects applied after eating.",
					foodPropertiesSchema()));
			tools.add(tool("set_placement_properties",
					"Required only when itemType=item and placeable=true. Set cross-plant or upright-torch geometry and placed light. Use supportProfile=overworld_vegetation for ordinary overworld plants, saplings, bushes, and flowers; the tool then guarantees grass-like substrates via #minecraft:supports_vegetation and adds the supplied special supports. Use explicit for special substrates or torches. Supports may use any_solid, block tags, or block IDs.",
					placementPropertiesSchema()));
		} else {
			tools.add(tool("set_texture",
					"Set the complete indexed 16x16 inventory icon. This does not control how the armor looks while equipped.",
					textureSchema()));
			tools.add(tool("fetch_armor_display_texture",
					"Fetch complete set_armor_display_texture-compatible 64x32 vanilla humanoid equipment UV sheets. Pass the intended armor slot; use a returned sheet as a UV-layout reference, then copy or recolor its palette/rows without moving body-part islands.",
					armorDisplayTextureFetchSchema()));
			tools.add(tool("set_armor_display_texture",
					"Required for armor. After choosing the armor slot, set the separate indexed 64x32 worn display texture used on the equipped humanoid model. The slot must match set_armor_properties. Keep unused UV space transparent. For head/chest/boots use the vanilla humanoid layout; leggings use humanoid_leggings. Call fetch_armor_display_texture for exact compatible examples and instructions.",
					armorDisplayTextureSchema()));
			tools.add(tool("set_armor_properties",
					requestedArmorSlot == null
							? "Infer the armor slot from the requested name, then set its native foil, enchantability, defense, toughness, knockback resistance, and durability."
							: "Set the required requested armor slot and its native foil, enchantability, defense, toughness, knockback resistance, and durability.",
					armorPropertiesSchema()));
		}
		tools.add(tool("inspect_behavior_api",
				"Inspect the required server-side Java marker and optional callback capability interfaces. Implement only the capabilities this content actually needs.", emptySchema()));
		tools.add(tool("search_java_classes",
				"Search the running Minecraft, Fabric, and Little Chemistry class graph by concept or class-name fragment while authoring behavior.",
				javaClassSearchSchema()));
		tools.add(tool("inspect_java_class",
				"Inspect a runtime Java class's hierarchy, constructors, fields, nested classes, and source-like method signatures without initializing it.",
				javaClassInspectSchema()));
		tools.add(tool("set_behavior_source",
				"Required: set a complete server-side Java compilation unit with no package declaration. Declare public final class GeneratedBehaviorImpl with a public no-argument constructor. It must implement DynamicBehavior plus only the callback capability interfaces it actually uses; a passive class implements only DynamicBehavior. Capability methods have no defaults.",
				behaviorSourceSchema()));
		tools.add(tool("compile_behavior",
				"Compile the required Java class against the running Little Chemistry and Minecraft classes. Use diagnostics to revise it until compilation succeeds.", emptySchema()));
		tools.add(tool("inspect_behavior_source",
				"Read the complete Java behavior source currently stored in the draft.", emptySchema()));
		tools.add(tool("inspect_draft", "Inspect missing required sections before submission.", emptySchema()));
		tools.add(tool("submit", "Validate and submit the completed definition. Compiled Java behavior is always required. This is the only successful finish.", emptySchema()));
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

	private static JsonObject armorDisplayTextureSchema() {
		JsonObject schema = objectSchema("slot", "palette", "rows");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("slot", enumSchema("head", "chest", "leggings", "boots"));
		properties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		properties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{64}$"), 32, 32));
		return schema;
	}

	private static JsonObject armorDisplayTextureFetchSchema() {
		JsonObject schema = objectSchema("query", "slot");
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject query = typeSchema("string");
		query.addProperty("minLength", 1);
		query.addProperty("maxLength", 80);
		properties.add("query", query);
		properties.add("slot", enumSchema("head", "chest", "leggings", "boots"));
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

	private static JsonObject metadataSchema() {
		JsonObject schema = objectSchema("rarity", "description");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("rarity", enumSchema("common", "uncommon", "rare", "epic", "legendary", "mythical"));
		JsonObject description = typeSchema("string");
		description.addProperty("minLength", 1);
		description.addProperty("maxLength", 120);
		properties.add("description", description);
		return schema;
	}

	private static JsonObject blockPropertiesSchema() {
		JsonObject schema = objectSchema("material", "hardness", "preferredTool", "requiresCorrectTool", "shape");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("material", enumSchema("stone", "metal", "wood", "glass", "crystal", "earth", "organic", "cloth"));
		properties.add("hardness", numberSchema(0, 50));
		properties.add("preferredTool", enumSchema("none", "pickaxe", "axe", "shovel", "hoe"));
		properties.add("requiresCorrectTool", typeSchema("boolean"));
		properties.add("shape", enumSchema("full_cube", "slab", "no_collision", "star", "fence", "cross", "torch", "custom"));
		return schema;
	}

	private static JsonObject blockModelSchema() {
		JsonObject texture = objectSchema("id", "width", "height", "palette", "rows");
		JsonObject textureProperties = texture.getAsJsonObject("properties");
		textureProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		textureProperties.add("width", integerSchema(1, 64));
		textureProperties.add("height", integerSchema(1, 64));
		textureProperties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		textureProperties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{1,64}$"), 1, 64));

		JsonObject defaultFaces = blockFacesSchema(true);
		JsonObject elementFaces = blockFacesSchema(false);
		JsonObject element = objectSchema("from", "to", "collision", "faces");
		JsonObject elementProperties = element.getAsJsonObject("properties");
		elementProperties.add("from", coordinateArraySchema(3));
		elementProperties.add("to", coordinateArraySchema(3));
		elementProperties.add("collision", typeSchema("boolean"));
		elementProperties.add("faces", elementFaces);

		JsonObject schema = objectSchema("textures", "particleTexture", "faces", "elements");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("textures", arraySchema(texture, 1, 12));
		properties.add("particleTexture", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		properties.add("faces", defaultFaces);
		properties.add("elements", arraySchema(element, 0, 24));
		return schema;
	}

	private static JsonObject blockFacesSchema(boolean requireAll) {
		JsonObject schema = requireAll
				? objectSchema("down", "up", "north", "south", "west", "east")
				: objectSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		for (String direction : new String[] {"down", "up", "north", "south", "west", "east"}) {
			properties.add(direction, blockFaceSchema());
		}
		return schema;
	}

	private static JsonObject blockFaceSchema() {
		JsonObject schema = objectSchema("texture");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("texture", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		properties.add("uv", coordinateArraySchema(4));
		return schema;
	}

	private static JsonObject coordinateArraySchema(int length) {
		return arraySchema(numberSchema(0, 16), length, length);
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
		JsonObject emitter = objectSchema("particle", "chancePerTick", "count", "velocity", "region");
		JsonObject properties = emitter.getAsJsonObject("properties");
		properties.add("particle", stringSchema("^(smoke|flame|portal|enchant|end_rod|electric_spark|glow|custom:[a-z][a-z0-9_]{0,31})$"));
		properties.add("chancePerTick", numberSchema(0, 0.25));
		properties.add("count", integerSchema(1, 4));
		properties.add("velocity", numberSchema(0, 0.2));
		properties.add("region", enumSchema("top", "all"));
		JsonObject schema = objectSchema("emitters");
		schema.getAsJsonObject("properties").add("emitters", arraySchema(emitter, 0, 2));
		return schema;
	}

	private static JsonObject customParticlesSchema() {
		JsonObject frame = objectSchema("palette", "rows");
		JsonObject frameProperties = frame.getAsJsonObject("properties");
		frameProperties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		frameProperties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{1,32}$"), 1, 32));

		JsonObject particle = objectSchema("id", "frames", "frameTicks", "loop", "lifetimeTicks",
				"startSize", "endSize", "startColor", "endColor", "gravity", "friction",
				"collision", "emissive", "spin");
		JsonObject properties = particle.getAsJsonObject("properties");
		properties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		properties.add("frames", arraySchema(frame, 1, 4));
		properties.add("frameTicks", integerSchema(1, 40));
		properties.add("loop", typeSchema("boolean"));
		properties.add("lifetimeTicks", integerSchema(1, 400));
		properties.add("startSize", numberSchema(0.01, 4));
		properties.add("endSize", numberSchema(0.01, 4));
		properties.add("startColor", stringSchema("^[0-9A-Fa-f]{8}$"));
		properties.add("endColor", stringSchema("^[0-9A-Fa-f]{8}$"));
		properties.add("gravity", numberSchema(-2, 2));
		properties.add("friction", numberSchema(0, 1));
		properties.add("collision", typeSchema("boolean"));
		properties.add("emissive", typeSchema("boolean"));
		properties.add("spin", numberSchema(-1, 1));

		JsonObject schema = objectSchema("particles");
		schema.getAsJsonObject("properties").add("particles", arraySchema(particle, 0, 4));
		return schema;
	}

	private static JsonObject itemPropertiesSchema() {
		JsonObject schema = objectSchema("itemType", "heldType", "maxStack", "foil", "enchantability", "reach", "placeable", "craftingUse");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("itemType", enumSchema("item", "food", "tool"));
		properties.add("heldType", enumSchema("regular", "tool", "rod", "bow", "crossbow", "mace", "spear"));
		properties.add("maxStack", integerSchema(1, 64));
		properties.add("foil", typeSchema("boolean"));
		properties.add("enchantability", integerSchema(0, 255));
		properties.add("reach", numberSchema(0, 16));
		properties.add("placeable", typeSchema("boolean"));
		properties.add("craftingUse", enumSchema("consume", "keep", "damage"));
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
		JsonObject schema = objectSchema("shape", "supportProfile", "supports", "lightLevel", "visuallyEmissive");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("shape", enumSchema("cross", "torch"));
		properties.add("supportProfile", enumSchema("overworld_vegetation", "explicit"));
		properties.add("supports", arraySchema(stringSchema("^(any_solid|#?[a-z0-9_.-]+:[a-z0-9_./-]+)$"), 1, 15));
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
		JsonObject schema = objectSchema("slot", "foil", "enchantability", "defense", "toughness",
				"knockbackResistance", "durability");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("slot", enumSchema("head", "chest", "leggings", "boots"));
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
