package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;

import java.io.IOException;

public final class ContentGenerationAgent {
	private static final Gson GSON = new Gson();
	private static final String SYSTEM_PROMPT = """
			Create the requested Minecraft item, block, armor piece, or entity with the tools. For recipe requests, derive a cohesive result
			from the supplied process and ingredients, reflecting them in its appearance, properties, and behavior. Search and inspect
			relevant previously generated content and fetch similar vanilla content for reference. Inspect complete generated behavior
			source and decompiled classpath Java method bodies when useful, but do not merely copy, reskin, or delegate to existing content
			when the concept implies special functionality. Complete every applicable property and texture. When distinctive effects
			improve the concept, author reusable custom particles and emit them from block ambience or GeneratedBehaviorImpl. Author and
			compile GeneratedBehaviorImpl, inspect the finished draft, then submit. For blocks, use the declarative drop tool for ordinary
			mining drops instead of spawning duplicate drops from Java callbacks. For every block, explicitly classify it by calling
			set_block_role. A workstation is a placed block whose primary purpose is to transform inventory inputs into persistent,
			server-cached AI-generated outputs. If workstation=true, define its complete policy and declarative UI, and implement both
			WorkstationBehavior and WorkstationTickBehavior. The fixed engine owns block entities, menus, screens, packets, registries,
			persistence, recipe transactions, and ticking; do not generate replacements for those classes. Generated behavior instances
			are shared definition singletons and workstation- or entity-capable classes may not declare fields, so store placement state
			only through DynamicWorkstationContext or per-instance DynamicEntityState. For an entity whose anatomy fits a supported animated
			vanilla model, adapt a fetched compatible UV sheet; use authored cuboids for original anatomy. WorkstationRecipeRequest aiContext
			is descriptive and excluded from cache identity; every contextual value that may affect output or recipeData must also enter a
			deterministic canonical cacheDiscriminator.
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
			String requestedName, int requestedOutputCount, JsonObject recipeContext)
			throws IOException, InterruptedException {
		return generateForRecipe(type, requestedArmorSlot, requestedName, requestedOutputCount, recipeContext, null);
	}

	public GeneratedContentSpec generateForRecipe(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, int requestedOutputCount, JsonObject recipeContext, String workstationPolicy)
			throws IOException, InterruptedException {
		if (recipeContext == null) throw new IllegalArgumentException("recipeContext");
		return generate(type, requestedArmorSlot, requestedName, requestedOutputCount, recipeContext,
				workstationPolicy);
	}

	private GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, int requestedOutputCount, JsonObject recipeContext)
			throws IOException, InterruptedException {
		return generate(type, requestedArmorSlot, requestedName, requestedOutputCount, recipeContext, null);
	}

	private GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, int requestedOutputCount, JsonObject recipeContext, String workstationPolicy)
			throws IOException, InterruptedException {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				type, requestedName, requestedArmorSlot, requestedOutputCount);
		JsonArray history = new JsonArray();
		JsonObject requestData = new JsonObject();
		requestData.addProperty("source", recipeContext == null ? "wand" : "recipe");
		requestData.addProperty("requestedKind", type.serializedName());
		requestData.addProperty("requestedName", requestedName);
		if (recipeContext != null) requestData.addProperty("requestedOutputCount", requestedOutputCount);
		if (recipeContext != null) requestData.add("recipe", recipeContext.deepCopy());
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
		if (type == DynamicContentType.ENTITY) {
			requestData.addProperty("entityVisualChoices", "Either author a rigid cuboid model with 1-64-pixel textures or reuse a supported animated vanilla profile with its exact fetched 32x32, 64x32, or 64x64 UV sheet");
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
			String systemPrompt = workstationPolicy == null ? SYSTEM_PROMPT
					: SYSTEM_PROMPT + "\nThis recipe is produced by an AI-defined workstation. Treat its persisted policy "
					+ "as process design guidance and make the selected recipeData visible in the output's identity, artwork, "
					+ "native properties, and Java behavior.\n<workstation_policy>\n" + workstationPolicy
					+ "\n</workstation_policy>\n";
			OpenAiClient.ToolRound response = openAi.runToolRound(systemPrompt, tools, history);
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
				ContentGenerationDraft.ToolExecution execution =
						GenerationInspectionTools.execute(call.name(), call.arguments());
				if (execution == null) {
					execution = switch (call.name()) {
						case "fetch" -> MinecraftContentFetcher.fetch(call.arguments());
						case "fetch_texture" -> MinecraftContentFetcher.fetchTexture(call.arguments());
						case "fetch_armor_display_texture" -> MinecraftContentFetcher.fetchArmorDisplayTexture(call.arguments());
						case "fetch_entity" -> MinecraftContentFetcher.fetchEntity(call.arguments());
						case "fetch_entity_visual" -> MinecraftContentFetcher.fetchEntityVisual(call.arguments());
						default -> draft.execute(call.name(), call.arguments());
					};
				}
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
				"Replace the content's reusable AI-authored particle library with zero to four definitions. Each definition has 1-4 square indexed frames, linear size/color transitions, animation, and bounded physics. Block ambient emitters reference custom:<id>. Server-side Java behavior must emit a declared string-literal local ID only through the budgeted com.yeyito.littlechemistry.particle.DynamicParticles.spawn API; never construct particle options or use the particle registry directly.",
				customParticlesSchema()));
		if (type == DynamicContentType.BLOCK) {
			tools.add(tool("set_block_role",
					"Required for every block: explicitly classify it as an ordinary block or an AI-defined workstation. Set workstation=true only when its primary purpose is repeatable inventory transformation.",
					blockRoleSchema()));
			tools.add(tool("set_workstation_policy",
					"Required only after set_block_role(workstation=true). Write a complete self-contained system prompt governing every future recipe in this workstation. Explain the process, every input/catalyst/fuel role, valid combinations, counts and consume/keep/damage uses, timing and environmental rules, output identity/count themes, and how the process influences output visuals, native properties, and Java behavior. Explicitly express every duration in Minecraft ticks and state that 20 ticks equal one second. Also supply a closed bounded JSON Schema for per-recipe process data interpreted by the generated Java. The schema root must be type=object with properties, required, and additionalProperties=false. Nested object/array/string/integer/number/boolean nodes may use descriptions, scalar enums, numeric/string/array bounds, but not oneOf, references, patterns, or open objects.",
					workstationPolicySchema()));
			tools.add(tool("set_workstation_layout",
					"Required only for workstations after their policy. Define stable named slots with exactly one primary role=output slot, title and player-inventory positions, colors, labels, gauges bound to signed-16-bit state channels, exactly one make_recipe button, and optional custom buttons whose visibility/enabled state may bind to channels. Generated Java controls mechanics; this declarative envelope lets every client safely render and interact with the runtime UI.",
					workstationLayoutSchema()));
			tools.add(tool("set_block_properties",
					"Choose gameplay properties and the visual/physical model category. Presets are full_cube, slab, no_collision, star, fence, cross, and torch. Use custom for an AI-authored model made from axis-aligned cuboids. Set directional=true only when the block has a meaningful front: the authored north face becomes its front, and its model and collision rotate horizontally to face the placer.",
					blockPropertiesSchema()));
			tools.add(tool("set_block_model",
					"Required after set_block_properties. Define 1-12 named indexed textures at any 1-64 pixel dimensions, choose the breaking-particle texture, and map each block face to a texture. Reuse a texture ID for a single-texture block or choose different top/bottom/sides. For a directional block, author its front on the north face. Omit UV to let preset/custom cuboid dimensions crop the texture like a vanilla model; supply UV in 0-16 model coordinates to override it. For shape=custom, define 1-24 axis-aligned cuboids with 0-16 bounds, collision control, and optional per-element face overrides; other shapes require an empty elements array.",
					blockModelSchema()));
			tools.add(tool("set_block_redstone", "Set constant weak redstone and comparator output; use zero for neither.", blockRedstoneSchema()));
			tools.add(tool("set_block_light", "Set true world light and visual emissive rendering.", lightSchema()));
			tools.add(tool("set_block_particles", "Replace the block's particle emitters; use an empty array for none. particle is one of smoke, flame, portal, enchant, end_rod, electric_spark, glow, or custom:<id> referencing set_custom_particles.", particlesSchema()));
			tools.add(tool("set_block_drops",
					"Required: define one primary drop and at most one different bonus drop. Use targetKind=self with target=self for the owning block, registered_item with a namespaced item ID returned by fetch, or dynamic_content with an existing generated contentId from the recipe context or search/inspection tools. A self entry must be exactly one with fortune=none. Enable silkTouchDropsSelf for ore-like blocks. ore_like Fortune multiplies non-self material drops; use none for armor, tools, and other singular targets. explosionDecay applies Minecraft-style per-item decay. Do not duplicate these ordinary drops in Java behavior.",
					blockDropsSchema()));
		} else if (type == DynamicContentType.ITEM) {
			tools.add(tool("set_texture", "Set the complete indexed 16x16 inventory texture.", textureSchema()));
			tools.add(tool("set_item_properties",
					"Choose gameplay itemType independently from the visual heldType pose. Examples: regular for materials, food, and small objects; tool for swords, pickaxes, axes, staffs, and wands; rod for fishing rods or reversed long rods; bow for bows; crossbow for crossbows; mace for hammers, clubs, and other heavy-headed weapons; spear for spears and lances. A pose does not add its example's mechanics or animations. Also set stack, foil, enchantability, reach, placeable properties, craftingUse, and fuelBurnTicks. Set fuelBurnTicks=0 when it is not furnace fuel; otherwise choose a duration using 20 ticks per second and 200 ticks per standard smelt. Vanilla references: stick=100, coal/charcoal=1600, blaze rod=2400, lava bucket=20000. Use consume normally, keep for unchanged reusable catalysts or molds, and damage for reusable tools such as cutters or hammers that lose one durability per craft. maxStack must accommodate requestedOutputCount when supplied.",
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
		} else if (type == DynamicContentType.ARMOR) {
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
		} else {
			tools.add(tool("set_texture",
					"Set the complete indexed 16x16 inventory icon for the consumable entity spawner item.",
					textureSchema()));
			tools.add(tool("fetch_entity",
					"Fetch dimensions, category, and native attributes of similar registered vanilla entities for gameplay reference.",
					entityFetchSchema()));
			tools.add(tool("fetch_entity_visual",
					"Fetch supported animated vanilla entity-model profiles with complete compatible indexed UV sheets. Choose a returned model and copy or recolor its palette/rows without moving UV islands, then pass it to set_entity_vanilla_model. Use set_entity_model instead when no existing anatomy fits.",
					entityFetchSchema()));
			tools.add(tool("set_entity_properties",
					"Set the generated mob's native movement carrier, disposition, dimensions, combat attributes, persistence-safe drops, and registered vanilla sounds. Ground and flying carriers navigate natively. Passive mobs never attack; neutral mobs retaliate; hostile mobs acquire players and attack.",
					entityPropertiesSchema()));
			tools.add(tool("set_entity_model",
					"Choose this instead of set_entity_vanilla_model when the entity needs original anatomy. Define a complete rigid model made from 1-24 axis-aligned cuboids in normalized 0-16 model coordinates and 1-12 named indexed textures. The renderer scales the complete 0-16 model volume to the entity width and height. Map every default face, use per-element face overrides when useful, and choose a particleTexture as the model's primary texture ID.",
					entityModelSchema()));
			tools.add(tool("set_entity_vanilla_model",
					"Choose one animated profile returned by fetch_entity_visual and set its complete compatible skin. Copy or recolor a returned indexed sheet while preserving every UV island, row width, and row count. This reuses Minecraft's articulated model and walk/look animation; it does not copy that vanilla mob's gameplay or special render layers.",
					entityVanillaModelSchema()));
		}
		tools.add(tool("inspect_behavior_api",
				"Inspect the required server-side Java marker and optional item, block, entity, and workstation callback capability interfaces. A workstation must implement both WorkstationBehavior and WorkstationTickBehavior, plus only the optional slot/button/automation capabilities it needs.", emptySchema()));
		GenerationInspectionTools.addTo(tools);
		tools.add(tool("set_behavior_source",
					"Required: set a complete server-side Java compilation unit with no package declaration. Declare public final class GeneratedBehaviorImpl with a public no-argument constructor. It must implement DynamicBehavior plus only the callback capability interfaces it actually uses; a passive class implements only DynamicBehavior. Capability methods have no defaults. A workstation- or entity-capable GeneratedBehaviorImpl must declare no fields; use the context's bounded state APIs instead.",
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
		JsonObject schema = objectSchema("material", "hardness", "preferredTool", "requiresCorrectTool", "shape", "directional");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("material", enumSchema("stone", "metal", "wood", "glass", "crystal", "earth", "organic", "cloth"));
		properties.add("hardness", numberSchema(0, 50));
		properties.add("preferredTool", enumSchema("none", "pickaxe", "axe", "shovel", "hoe"));
		properties.add("requiresCorrectTool", typeSchema("boolean"));
		properties.add("shape", enumSchema("full_cube", "slab", "no_collision", "star", "fence", "cross", "torch", "custom"));
		properties.add("directional", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject blockModelSchema() {
		return cuboidModelSchema(true);
	}

	private static JsonObject entityModelSchema() {
		return cuboidModelSchema(false);
	}

	private static JsonObject entityVanillaModelSchema() {
		JsonObject schema = objectSchema("profile", "width", "height", "palette", "rows");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("profile", enumSchema("zombie", "skeleton", "enderman", "cow", "pig", "spider",
				"creeper", "blaze", "cod"));
		properties.add("width", integerSchema(32, 64));
		properties.add("height", integerSchema(32, 64));
		properties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		properties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{32}$|^[0-9A-Fa-f]{64}$"), 32, 64));
		return schema;
	}

	private static JsonObject cuboidModelSchema(boolean includeCollision) {
		JsonObject texture = objectSchema("id", "width", "height", "palette", "rows");
		JsonObject textureProperties = texture.getAsJsonObject("properties");
		textureProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		textureProperties.add("width", integerSchema(1, 64));
		textureProperties.add("height", integerSchema(1, 64));
		textureProperties.add("palette", arraySchema(stringSchema("^[0-9A-Fa-f]{8}$"), 1, 16));
		textureProperties.add("rows", arraySchema(stringSchema("^[0-9A-Fa-f]{1,64}$"), 1, 64));

		JsonObject defaultFaces = blockFacesSchema(true);
		JsonObject elementFaces = blockFacesSchema(false);
		JsonObject element = includeCollision
				? objectSchema("from", "to", "collision", "faces")
				: objectSchema("from", "to", "faces");
		JsonObject elementProperties = element.getAsJsonObject("properties");
		elementProperties.add("from", coordinateArraySchema(3));
		elementProperties.add("to", coordinateArraySchema(3));
		if (includeCollision) elementProperties.add("collision", typeSchema("boolean"));
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

	private static JsonObject blockDropsSchema() {
		JsonObject entry = objectSchema("targetKind", "target", "minCount", "maxCount", "chance", "fortune");
		JsonObject entryProperties = entry.getAsJsonObject("properties");
		entryProperties.add("targetKind", enumSchema("self", "registered_item", "dynamic_content"));
		entryProperties.add("target", stringSchema("^(self|[a-z0-9_.-]+:[a-z0-9_./-]+)$"));
		entryProperties.add("minCount", integerSchema(1, 64));
		entryProperties.add("maxCount", integerSchema(1, 64));
		entryProperties.add("chance", numberSchema(0.01, 1));
		entryProperties.add("fortune", enumSchema("none", "ore_like"));

		JsonObject schema = objectSchema("entries", "silkTouchDropsSelf", "explosionDecay");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("entries", arraySchema(entry, 1, 2));
		properties.add("silkTouchDropsSelf", typeSchema("boolean"));
		properties.add("explosionDecay", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject itemPropertiesSchema() {
		JsonObject schema = objectSchema("itemType", "heldType", "maxStack", "foil", "enchantability", "reach",
				"placeable", "craftingUse", "fuelBurnTicks");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("itemType", enumSchema("item", "food", "tool"));
		properties.add("heldType", enumSchema("regular", "tool", "rod", "bow", "crossbow", "mace", "spear"));
		properties.add("maxStack", integerSchema(1, 64));
		properties.add("foil", typeSchema("boolean"));
		properties.add("enchantability", integerSchema(0, 255));
		properties.add("reach", numberSchema(0, 16));
		properties.add("placeable", typeSchema("boolean"));
		properties.add("craftingUse", enumSchema("consume", "keep", "damage"));
		properties.add("fuelBurnTicks", integerSchema(0, DynamicItemProperties.MAX_FUEL_BURN_TICKS));
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

	private static JsonObject entityFetchSchema() {
		JsonObject schema = objectSchema("query");
		JsonObject query = typeSchema("string");
		query.addProperty("minLength", 1);
		query.addProperty("maxLength", 80);
		schema.getAsJsonObject("properties").add("query", query);
		return schema;
	}

	private static JsonObject entityPropertiesSchema() {
		JsonObject drop = objectSchema("item", "minimum", "maximum", "chance");
		JsonObject dropProperties = drop.getAsJsonObject("properties");
		dropProperties.add("item", stringSchema("^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
		dropProperties.add("minimum", integerSchema(0, 64));
		dropProperties.add("maximum", integerSchema(0, 64));
		dropProperties.add("chance", numberSchema(0, 1));

		JsonObject schema = objectSchema("movement", "disposition", "width", "height", "eyeHeight",
				"maxHealth", "movementSpeed", "attackDamage", "armor", "knockbackResistance",
				"followRange", "experienceReward", "fireImmune", "ambientSound", "hurtSound",
				"deathSound", "drops");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("movement", enumSchema("ground", "flying"));
		properties.add("disposition", enumSchema("passive", "neutral", "hostile"));
		properties.add("width", numberSchema(0.1, 8));
		properties.add("height", numberSchema(0.1, 8));
		properties.add("eyeHeight", numberSchema(0, 8));
		properties.add("maxHealth", numberSchema(1, 1024));
		properties.add("movementSpeed", numberSchema(0, 2));
		properties.add("attackDamage", numberSchema(0, 2048));
		properties.add("armor", numberSchema(0, 30));
		properties.add("knockbackResistance", numberSchema(0, 1));
		properties.add("followRange", numberSchema(1, 128));
		properties.add("experienceReward", integerSchema(0, 10_000));
		properties.add("fireImmune", typeSchema("boolean"));
		JsonObject sound = stringSchema("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
		properties.add("ambientSound", sound.deepCopy());
		properties.add("hurtSound", sound.deepCopy());
		properties.add("deathSound", sound.deepCopy());
		properties.add("drops", arraySchema(drop, 0, 16));
		return schema;
	}

	private static JsonObject blockRoleSchema() {
		JsonObject schema = objectSchema("workstation");
		schema.getAsJsonObject("properties").add("workstation", typeSchema("boolean"));
		return schema;
	}

	private static JsonObject workstationPolicySchema() {
		JsonObject schema = objectSchema("processDescription", "recipeSystemPrompt", "recipeDataSchema");
		JsonObject properties = schema.getAsJsonObject("properties");
		JsonObject description = typeSchema("string");
		description.addProperty("minLength", 1);
		description.addProperty("maxLength", 1024);
		properties.add("processDescription", description);
		JsonObject prompt = typeSchema("string");
		prompt.addProperty("minLength", 1);
		prompt.addProperty("maxLength", 16_384);
		properties.add("recipeSystemPrompt", prompt);
		JsonObject recipeSchema = typeSchema("object");
		recipeSchema.addProperty("additionalProperties", true);
		properties.add("recipeDataSchema", recipeSchema);
		return schema;
	}

	private static JsonObject workstationLayoutSchema() {
		JsonObject slot = objectSchema("id", "role", "x", "y", "maxStack",
				"allowPlayerInsert", "allowPlayerExtract");
		JsonObject slotProperties = slot.getAsJsonObject("properties");
		slotProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		slotProperties.add("role", enumSchema("input", "catalyst", "fuel", "output", "byproduct", "storage", "custom"));
		slotProperties.add("x", integerSchema(0, 319));
		slotProperties.add("y", integerSchema(0, 255));
		slotProperties.add("maxStack", integerSchema(1, 64));
		slotProperties.add("allowPlayerInsert", typeSchema("boolean"));
		slotProperties.add("allowPlayerExtract", typeSchema("boolean"));
		slotProperties.add("emptySlotIcon", stringSchema("^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
		JsonObject optionalText = typeSchema("string");
		optionalText.addProperty("maxLength", 256);
		slotProperties.add("tooltip", optionalText);

		JsonObject label = objectSchema("id", "text", "x", "y", "color", "shadow");
		JsonObject labelProperties = label.getAsJsonObject("properties");
		labelProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		JsonObject labelText = typeSchema("string"); labelText.addProperty("maxLength", 256);
		labelProperties.add("text", labelText);
		labelProperties.add("x", integerSchema(0, 319));
		labelProperties.add("y", integerSchema(0, 255));
		labelProperties.add("color", stringSchema("^[0-9A-Fa-f]{8}$"));
		labelProperties.add("shadow", typeSchema("boolean"));
		labelProperties.add("tooltip", optionalText.deepCopy());

		JsonObject gauge = objectSchema("id", "channel", "x", "y", "width", "height", "direction",
				"fillColor", "backgroundColor");
		JsonObject gaugeProperties = gauge.getAsJsonObject("properties");
		gaugeProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		gaugeProperties.add("channel", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		gaugeProperties.add("x", integerSchema(0, 319));
		gaugeProperties.add("y", integerSchema(0, 255));
		gaugeProperties.add("width", integerSchema(1, 256));
		gaugeProperties.add("height", integerSchema(1, 256));
		gaugeProperties.add("direction", enumSchema("left_to_right", "right_to_left", "top_to_bottom", "bottom_to_top"));
		gaugeProperties.add("fillColor", stringSchema("^[0-9A-Fa-f]{8}$"));
		gaugeProperties.add("backgroundColor", stringSchema("^[0-9A-Fa-f]{8}$"));
		gaugeProperties.add("tooltip", optionalText.deepCopy());

		JsonObject button = objectSchema("id", "role", "label", "x", "y", "width", "height");
		JsonObject buttonProperties = button.getAsJsonObject("properties");
		buttonProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		buttonProperties.add("role", enumSchema("make_recipe", "custom"));
		JsonObject buttonLabel = typeSchema("string"); buttonLabel.addProperty("maxLength", 64);
		buttonProperties.add("label", buttonLabel);
		buttonProperties.add("x", integerSchema(0, 319));
		buttonProperties.add("y", integerSchema(0, 255));
		buttonProperties.add("width", integerSchema(20, 256));
		buttonProperties.add("height", integerSchema(12, 40));
		buttonProperties.add("tooltip", optionalText.deepCopy());
		buttonProperties.add("visibleChannel", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		buttonProperties.add("enabledChannel", stringSchema("^[a-z][a-z0-9_]{0,31}$"));

		JsonObject channel = objectSchema("id", "minimum", "maximum", "initialValue");
		JsonObject channelProperties = channel.getAsJsonObject("properties");
		channelProperties.add("id", stringSchema("^[a-z][a-z0-9_]{0,31}$"));
		channelProperties.add("minimum", integerSchema(Short.MIN_VALUE, Short.MAX_VALUE));
		channelProperties.add("maximum", integerSchema(Short.MIN_VALUE, Short.MAX_VALUE));
		channelProperties.add("initialValue", integerSchema(Short.MIN_VALUE, Short.MAX_VALUE));

		JsonObject ui = objectSchema("width", "height", "playerInventoryX", "playerInventoryY", "titleX", "titleY",
				"playerInventoryLabelX", "playerInventoryLabelY", "backgroundColor",
				"labels", "gauges", "buttons", "stateChannels");
		JsonObject uiProperties = ui.getAsJsonObject("properties");
		uiProperties.add("width", integerSchema(176, 320));
		uiProperties.add("height", integerSchema(120, 256));
		uiProperties.add("playerInventoryX", integerSchema(0, 319));
		uiProperties.add("playerInventoryY", integerSchema(0, 255));
		uiProperties.add("titleX", integerSchema(0, 319));
		uiProperties.add("titleY", integerSchema(0, 255));
		uiProperties.add("playerInventoryLabelX", integerSchema(0, 319));
		uiProperties.add("playerInventoryLabelY", integerSchema(0, 255));
		uiProperties.add("backgroundColor", stringSchema("^[0-9A-Fa-f]{8}$"));
		uiProperties.add("labels", arraySchema(label, 0, 32));
		uiProperties.add("gauges", arraySchema(gauge, 0, 16));
		uiProperties.add("buttons", arraySchema(button, 1, 16));
		uiProperties.add("stateChannels", arraySchema(channel, 0, 32));

		JsonObject schema = objectSchema("slots", "ui");
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("slots", arraySchema(slot, 2, 54));
		properties.add("ui", ui);
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
