package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.content.DynamicArmorProperties;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBreakingPower;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicFoodEffect;
import com.yeyito.littlechemistry.content.DynamicFoodProperties;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicMaterial;
import com.yeyito.littlechemistry.content.DynamicParticleEmitter;
import com.yeyito.littlechemistry.content.DynamicParticleType;
import com.yeyito.littlechemistry.content.DynamicPlacedShape;
import com.yeyito.littlechemistry.content.DynamicPlacementProperties;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicTool;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

final class ContentGenerationDraft {
	private final DynamicContentType type;
	private final String requestedName;
	private final DynamicArmorSlot requestedArmorSlot;
	private DynamicTextureSpec texture;
	private DynamicMaterial material;
	private Float hardness;
	private DynamicTool preferredTool;
	private Boolean requiresCorrectTool;
	private DynamicBlockShape blockShape;
	private Integer redstonePower;
	private Integer comparatorPower;
	private Integer lightLevel;
	private Boolean visuallyEmissive;
	private List<DynamicParticleEmitter> particles;
	private Integer maxStack;
	private Rarity rarity;
	private Boolean foil;
	private DynamicItemType itemType;
	private DynamicHeldType heldType;
	private Integer enchantability;
	private DynamicFoodProperties foodProperties;
	private Boolean placeable;
	private DynamicPlacementProperties placementProperties;
	private DynamicTool itemTool;
	private DynamicBreakingPower breakingPower;
	private Float breakingSpeed;
	private Double reach;
	private Double attackDamage;
	private Double attackSpeed;
	private Integer durability;
	private Integer damagePerBlock;
	private Integer damagePerAttack;
	private Rarity armorRarity;
	private Boolean armorFoil;
	private Integer armorEnchantability;
	private Double armorDefense;
	private Double armorToughness;
	private Double armorKnockbackResistance;
	private Integer armorDurability;
	private String behaviorSource;
	private boolean behaviorCompiled;

	ContentGenerationDraft(DynamicContentType type, String requestedName) {
		this(type, requestedName, null);
	}

	ContentGenerationDraft(DynamicContentType type, String requestedName, DynamicArmorSlot requestedArmorSlot) {
		this.type = type;
		this.requestedName = requestedName;
		this.requestedArmorSlot = requestedArmorSlot;
		if ((type == DynamicContentType.ARMOR) != (requestedArmorSlot != null)) {
			throw new IllegalArgumentException("Armor generation requires a head, chest, leggings, or boots slot");
		}
	}

	ToolExecution execute(String tool, JsonObject arguments) {
		try {
			if (arguments.has("_malformed")) {
				throw new IllegalArgumentException("Tool arguments were not valid JSON");
			}
			return switch (tool) {
				case "set_texture" -> setTexture(arguments);
				case "set_block_properties" -> requireBlock(() -> setBlockProperties(arguments));
				case "set_block_redstone" -> requireBlock(() -> setBlockRedstone(arguments));
				case "set_block_light" -> requireBlock(() -> setBlockLight(arguments));
				case "set_block_particles" -> requireBlock(() -> setBlockParticles(arguments));
				case "set_item_properties" -> requireItem(() -> setItemProperties(arguments));
				case "set_tool_properties" -> requireItem(() -> setToolProperties(arguments));
				case "set_food_properties" -> requireItem(() -> setFoodProperties(arguments));
				case "set_placement_properties" -> requireItem(() -> setPlacementProperties(arguments));
				case "set_armor_properties" -> requireArmor(() -> setArmorProperties(arguments));
				case "inspect_behavior_api" -> inspectBehaviorApi(arguments);
				case "set_behavior_source" -> setBehaviorSource(arguments);
				case "compile_behavior" -> compileBehavior(arguments);
				case "inspect_behavior_source" -> inspectBehaviorSource(arguments);
				case "inspect_draft" -> ToolExecution.success(inspect(), null);
				case "submit" -> submit(arguments);
				default -> ToolExecution.error("UNKNOWN_TOOL", "Unknown generation tool: " + tool);
			};
		} catch (IllegalArgumentException error) {
			return ToolExecution.error("INVALID_ARGUMENT", safeMessage(error));
		} catch (Exception error) {
			return ToolExecution.error("TOOL_FAILURE", safeMessage(error));
		}
	}

	private ToolExecution setTexture(JsonObject arguments) {
		requireOnly(arguments, "palette", "rows");
		List<String> palette = strings(requiredArray(arguments, "palette"));
		List<String> rows = strings(requiredArray(arguments, "rows"));
		DynamicTextureSpec candidate = new DynamicTextureSpec(palette, rows);
		Set<Integer> usedColors = usedColors(rows);
		int[] colorCounts = colorCounts(rows);
		int luminanceRange = luminanceRange(palette, usedColors);
		if (type == DynamicContentType.BLOCK) {
			candidate.requireOpaque();
			if (usedColors.size() < 4) {
				throw new IllegalArgumentException("Block texture uses fewer than four palette colors; add readable material shading and accents");
			}
			if (luminanceRange < 48) {
				throw new IllegalArgumentException("Block texture contrast is too low; increase the luminance range to at least 48");
			}
			int substantialColors = 0;
			int dominantPixels = 0;
			for (int count : colorCounts) {
				if (count >= 8) substantialColors++;
				dominantPixels = Math.max(dominantPixels, count);
			}
			if (substantialColors < 4 || dominantPixels > 184) {
				throw new IllegalArgumentException("Block texture is too visually flat; use at least four colors across substantial pixel clusters and keep the dominant color below 72% of the image");
			}
			double symmetry = symmetryScore(rows);
			if (symmetry > 0.82) {
				throw new IllegalArgumentException(String.format(Locale.ROOT,
						"Block texture symmetry is %.2f; revise it below 0.82 with irregular material detail rather than a mirrored gradient or emblem",
						symmetry));
			}
		} else {
			candidate.requireBinaryAlpha();
			if (usedColors.size() < 3 || luminanceRange < 40) {
				throw new IllegalArgumentException("Item or armor texture needs at least three used colors and stronger readable contrast");
			}
		}
		texture = candidate;
		JsonObject details = new JsonObject();
		details.addProperty("paletteColors", palette.size());
		details.addProperty("paletteColorsUsed", usedColors.size());
		details.addProperty("luminanceRange", luminanceRange);
		details.addProperty("dominantColorFraction", java.util.Arrays.stream(colorCounts).max().orElse(0) / 256.0);
		details.addProperty("symmetryScore", symmetryScore(rows));
		details.addProperty("message", "The 16x16 texture was accepted.");
		return ToolExecution.success(details, null);
	}

	private ToolExecution setBlockProperties(JsonObject arguments) {
		requireOnly(arguments, "material", "hardness", "preferredTool", "requiresCorrectTool", "shape");
		material = DynamicMaterial.parse(requiredString(arguments, "material"));
		hardness = requiredFloat(arguments, "hardness");
		preferredTool = DynamicTool.parse(requiredString(arguments, "preferredTool"));
		requiresCorrectTool = requiredBoolean(arguments, "requiresCorrectTool");
		blockShape = DynamicBlockShape.parse(requiredString(arguments, "shape"));
		if (!Float.isFinite(hardness) || hardness < 0.0F || hardness > 50.0F) {
			throw new IllegalArgumentException("hardness must be between 0 and 50");
		}
		return ToolExecution.success(message("Block physical properties were accepted."), null);
	}

	private ToolExecution setBlockRedstone(JsonObject arguments) {
		requireOnly(arguments, "redstonePower", "comparatorPower");
		redstonePower = requiredInt(arguments, "redstonePower");
		comparatorPower = requiredInt(arguments, "comparatorPower");
		if (redstonePower < 0 || redstonePower > 15 || comparatorPower < 0 || comparatorPower > 15) {
			throw new IllegalArgumentException("redstonePower and comparatorPower must be between 0 and 15");
		}
		return ToolExecution.success(message("Block redstone properties were accepted."), null);
	}

	private ToolExecution setBlockLight(JsonObject arguments) {
		requireOnly(arguments, "level", "visuallyEmissive");
		lightLevel = requiredInt(arguments, "level");
		visuallyEmissive = requiredBoolean(arguments, "visuallyEmissive");
		if (lightLevel < 0 || lightLevel > 15) {
			throw new IllegalArgumentException("level must be between 0 and 15");
		}
		return ToolExecution.success(message("Block light properties were accepted."), null);
	}

	private ToolExecution setBlockParticles(JsonObject arguments) {
		requireOnly(arguments, "emitters");
		JsonArray encoded = requiredArray(arguments, "emitters");
		if (encoded.size() > 2) {
			throw new IllegalArgumentException("At most two particle emitters are allowed");
		}
		List<DynamicParticleEmitter> decoded = new ArrayList<>();
		for (JsonElement element : encoded) {
			if (!(element instanceof JsonObject emitter)) {
				throw new IllegalArgumentException("Every emitter must be an object");
			}
			requireOnly(emitter, "type", "chancePerTick", "count", "velocity", "region");
			String region = requiredString(emitter, "region");
			if (!region.equals("top") && !region.equals("all")) {
				throw new IllegalArgumentException("Particle region must be 'top' or 'all'");
			}
			decoded.add(new DynamicParticleEmitter(
					DynamicParticleType.parse(requiredString(emitter, "type")),
					requiredDouble(emitter, "chancePerTick"),
					requiredInt(emitter, "count"),
					requiredDouble(emitter, "velocity"),
					region.equals("top")
			));
		}
		particles = List.copyOf(decoded);
		return ToolExecution.success(message("Block particle properties were accepted."), null);
	}

	private ToolExecution setItemProperties(JsonObject arguments) {
		requireOnly(arguments, "itemType", "heldType", "maxStack", "rarity", "foil", "enchantability", "reach", "placeable");
		itemType = DynamicItemType.parse(requiredString(arguments, "itemType"));
		heldType = DynamicHeldType.parse(requiredString(arguments, "heldType"));
		maxStack = requiredInt(arguments, "maxStack");
		rarity = Rarity.valueOf(requiredString(arguments, "rarity").toUpperCase(Locale.ROOT));
		foil = requiredBoolean(arguments, "foil");
		enchantability = requiredInt(arguments, "enchantability");
		reach = requiredDouble(arguments, "reach");
		placeable = requiredBoolean(arguments, "placeable");
		if (itemType != DynamicItemType.FOOD) foodProperties = null;
		if (!placeable) placementProperties = null;
		if (itemType != DynamicItemType.ITEM && placeable) throw new IllegalArgumentException("Only ordinary items can be placeable");
		if (itemType == DynamicItemType.ITEM) {
			new DynamicItemProperties(itemType, heldType, maxStack, rarity, foil, enchantability, reach,
					DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0, null, null);
		} else if (itemType == DynamicItemType.TOOL && maxStack != 1) {
			throw new IllegalArgumentException("Tools must use maxStack 1");
		}
		return ToolExecution.success(message("General item properties were accepted."), null);
	}

	private ToolExecution setToolProperties(JsonObject arguments) {
		if (itemType != DynamicItemType.TOOL) {
			return ToolExecution.error("WRONG_ITEM_TYPE", "Set itemType=tool before setting tool properties");
		}
		requireOnly(arguments, "tool", "breakingPower", "breakingSpeed", "attackDamage", "attackSpeed",
				"durability", "damagePerBlock", "damagePerAttack");
		itemTool = DynamicTool.parse(requiredString(arguments, "tool"));
		breakingPower = DynamicBreakingPower.parse(requiredString(arguments, "breakingPower"));
		breakingSpeed = requiredFloat(arguments, "breakingSpeed");
		attackDamage = requiredDouble(arguments, "attackDamage");
		attackSpeed = requiredDouble(arguments, "attackSpeed");
		durability = requiredInt(arguments, "durability");
		damagePerBlock = requiredInt(arguments, "damagePerBlock");
		damagePerAttack = requiredInt(arguments, "damagePerAttack");
		new DynamicItemProperties(itemType, heldType, maxStack, rarity, foil, enchantability, reach,
				itemTool, breakingPower, breakingSpeed, attackDamage, attackSpeed,
				durability, damagePerBlock, damagePerAttack, null, null);
		return ToolExecution.success(message("Tool mining, combat, and durability properties were accepted."), null);
	}

	private ToolExecution setFoodProperties(JsonObject arguments) {
		if (itemType != DynamicItemType.FOOD) {
			return ToolExecution.error("WRONG_ITEM_TYPE", "Set itemType=food before setting food properties");
		}
		requireOnly(arguments, "hunger", "saturation", "consumeSeconds", "alwaysEdible", "effects");
		JsonArray encodedEffects = requiredArray(arguments, "effects");
		if (encodedEffects.size() > 32) throw new IllegalArgumentException("Food may have at most 32 status effects");
		List<DynamicFoodEffect> effects = new ArrayList<>();
		for (JsonElement element : encodedEffects) {
			if (!(element instanceof JsonObject effect)) throw new IllegalArgumentException("Every food effect must be an object");
			requireOnly(effect, "effect", "durationSeconds", "amplifier", "probability", "ambient", "showParticles", "showIcon");
			effects.add(new DynamicFoodEffect(
					Identifier.parse(requiredString(effect, "effect")),
					requiredFloat(effect, "durationSeconds"),
					requiredInt(effect, "amplifier"),
					requiredFloat(effect, "probability"),
					requiredBoolean(effect, "ambient"),
					requiredBoolean(effect, "showParticles"),
					requiredBoolean(effect, "showIcon")
			));
		}
		foodProperties = new DynamicFoodProperties(
				requiredInt(arguments, "hunger"), requiredFloat(arguments, "saturation"),
				requiredFloat(arguments, "consumeSeconds"), requiredBoolean(arguments, "alwaysEdible"), effects);
		return ToolExecution.success(message("Food hunger, saturation, and status effects were accepted."), null);
	}

	private ToolExecution setPlacementProperties(JsonObject arguments) {
		if (itemType != DynamicItemType.ITEM || !Boolean.TRUE.equals(placeable)) {
			return ToolExecution.error("WRONG_ITEM_TYPE", "Set itemType=item and placeable=true before setting placement properties");
		}
		requireOnly(arguments, "shape", "supports", "lightLevel", "visuallyEmissive");
		placementProperties = new DynamicPlacementProperties(
				DynamicPlacedShape.parse(requiredString(arguments, "shape")),
				strings(requiredArray(arguments, "supports")),
				requiredInt(arguments, "lightLevel"),
				requiredBoolean(arguments, "visuallyEmissive")
		);
		return ToolExecution.success(message("Item placement properties were accepted."), null);
	}

	private ToolExecution setArmorProperties(JsonObject arguments) {
		requireOnly(arguments, "slot", "rarity", "foil", "enchantability", "defense", "toughness",
				"knockbackResistance", "durability");
		DynamicArmorSlot slot = DynamicArmorSlot.parse(requiredString(arguments, "slot"));
		if (slot != requestedArmorSlot) {
			throw new IllegalArgumentException("Armor slot must be " + requestedArmorSlot.serializedName());
		}
		Rarity candidateRarity = Rarity.valueOf(requiredString(arguments, "rarity").toUpperCase(Locale.ROOT));
		boolean candidateFoil = requiredBoolean(arguments, "foil");
		int candidateEnchantability = requiredInt(arguments, "enchantability");
		double candidateDefense = requiredDouble(arguments, "defense");
		double candidateToughness = requiredDouble(arguments, "toughness");
		double candidateKnockbackResistance = requiredDouble(arguments, "knockbackResistance");
		int candidateDurability = requiredInt(arguments, "durability");
		new DynamicArmorProperties(requestedArmorSlot, candidateRarity, candidateFoil, candidateEnchantability,
				candidateDefense, candidateToughness, candidateKnockbackResistance, candidateDurability);
		armorRarity = candidateRarity;
		armorFoil = candidateFoil;
		armorEnchantability = candidateEnchantability;
		armorDefense = candidateDefense;
		armorToughness = candidateToughness;
		armorKnockbackResistance = candidateKnockbackResistance;
		armorDurability = candidateDurability;
		return ToolExecution.success(message("Armor slot, protection, and durability properties were accepted."), null);
	}

	private ToolExecution inspectBehaviorApi(JsonObject arguments) {
		requireOnly(arguments);
		JsonObject details = new JsonObject();
		details.addProperty("requiredClass", "public final class GeneratedBehaviorImpl implements DynamicBehavior");
		details.addProperty("callbacks", "The API includes held-item use in air/on blocks/on entities, inventory ticks, attacks, mining, consumption, crafting, placed-block use/attack/placement/breaking, entity contact/falls, random and scheduled ticks, neighbor updates, and projectile hits.");
		details.addProperty("inspection", "Call inspect_java_class for com.yeyito.littlechemistry.behavior.DynamicBehavior and its context records to get the exact current signatures.");
		details.addProperty("airContext", "context.level(): ServerLevel; context.player(): ServerPlayer; context.hand(): InteractionHand; context.stack(): ItemStack; context.definition(): DynamicContentDefinition");
		details.addProperty("blockContext", "All air fields plus context.clickedPos(): BlockPos; context.clickedFace(): Direction; context.clickLocation(): Vec3; context.adjacentPos(): BlockPos");
		details.addProperty("semantics", "Callbacks run only on the authoritative server. Return PASS for normal placement, eating, equipping, or block-item fallback; return SUCCESS or CONSUME after handling, and FAIL to reject the use.");
		details.addProperty("example", """
				import com.yeyito.littlechemistry.behavior.*;
				import net.minecraft.network.chat.Component;
				import net.minecraft.world.InteractionResult;

				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}

				    @Override
				    public InteractionResult useAir(DynamicItemUseContext context) {
				        context.player().sendSystemMessage(Component.literal("It works!"));
				        return InteractionResult.SUCCESS;
				    }

				    @Override
				    public InteractionResult useOnBlock(DynamicBlockUseContext context) {
				        return InteractionResult.PASS;
				    }
				}
				""");
		return ToolExecution.success(details, null);
	}

	private ToolExecution inspectBehaviorSource(JsonObject arguments) {
		requireOnly(arguments);
		if (behaviorSource == null) {
			return ToolExecution.error("MISSING_BEHAVIOR_SOURCE", "No Java behavior source is currently stored");
		}
		JsonObject details = new JsonObject();
		details.addProperty("source", behaviorSource);
		details.addProperty("compiled", behaviorCompiled);
		details.addProperty("sourceCharacters", behaviorSource.length());
		return ToolExecution.success(details, null);
	}

	private ToolExecution setBehaviorSource(JsonObject arguments) {
		requireOnly(arguments, "source");
		String source = requiredString(arguments, "source").strip();
		if (source.isEmpty() || source.length() > 60_000 || source.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("source must contain 1-60,000 valid text characters");
		}
		behaviorSource = source;
		behaviorCompiled = false;
		return ToolExecution.success(message("Behavior source was stored. Call compile_behavior before submitting."), null);
	}

	private ToolExecution compileBehavior(JsonObject arguments) {
		requireOnly(arguments);
		if (behaviorSource == null) {
			return ToolExecution.error("MISSING_BEHAVIOR_SOURCE", "Call set_behavior_source before compile_behavior");
		}
		try {
			DynamicBehaviorCompiler.Compiled compiled = DynamicBehaviorCompiler.compile(behaviorSource);
			behaviorSource = compiled.source();
			behaviorCompiled = true;
			JsonObject details = message("Java behavior compiled successfully.");
			details.addProperty("className", DynamicBehaviorCompiler.GENERATED_CLASS_NAME);
			details.addProperty("sourceCharacters", behaviorSource.length());
			return ToolExecution.success(details, null);
		} catch (IllegalArgumentException error) {
			behaviorCompiled = false;
			return ToolExecution.error("JAVA_COMPILATION_FAILED", safeMessage(error));
		}
	}

	private ToolExecution submit(JsonObject arguments) {
		requireOnly(arguments);
		List<String> missing = missing();
		if (!missing.isEmpty()) {
			JsonObject details = new JsonObject();
			details.addProperty("ok", false);
			details.addProperty("code", "INCOMPLETE_DRAFT");
			JsonArray values = new JsonArray();
			missing.forEach(values::add);
			details.add("missing", values);
			details.addProperty("message", "Set every required section, then call submit again.");
			return new ToolExecution(details, null);
		}
		GeneratedContentSpec generated;
		if (type == DynamicContentType.BLOCK) {
			generated = new GeneratedContentSpec(
					texture,
					new DynamicBlockProperties(material, hardness, preferredTool, requiresCorrectTool,
							blockShape, redstonePower, comparatorPower, lightLevel, visuallyEmissive, particles),
					null,
					null,
					behaviorSource
			);
		} else if (type == DynamicContentType.ITEM) {
			DynamicItemProperties item = itemType == DynamicItemType.TOOL
					? new DynamicItemProperties(itemType, heldType, maxStack, rarity, foil, enchantability, reach,
							itemTool, breakingPower, breakingSpeed, attackDamage, attackSpeed,
							durability, damagePerBlock, damagePerAttack, null, null)
					: new DynamicItemProperties(itemType, heldType, maxStack, rarity, foil, enchantability, reach,
							DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0,
							itemType == DynamicItemType.FOOD ? foodProperties : null,
							itemType == DynamicItemType.ITEM && Boolean.TRUE.equals(placeable) ? placementProperties : null);
			generated = new GeneratedContentSpec(texture, null, item, null, behaviorSource);
		} else {
			generated = new GeneratedContentSpec(texture, null, null,
					new DynamicArmorProperties(requestedArmorSlot, armorRarity, armorFoil, armorEnchantability,
							armorDefense, armorToughness, armorKnockbackResistance, armorDurability),
					behaviorSource);
		}
		JsonObject details = message("The draft passed validation and was submitted.");
		details.addProperty("submitted", true);
		return ToolExecution.success(details, generated);
	}

	private JsonObject inspect() {
		JsonObject result = new JsonObject();
		result.addProperty("ok", true);
		result.addProperty("kind", type.serializedName());
		result.addProperty("requestedName", requestedName);
		if (requestedArmorSlot != null) result.addProperty("requestedArmorSlot", requestedArmorSlot.serializedName());
		JsonArray missing = new JsonArray();
		missing().forEach(missing::add);
		result.add("missing", missing);
		result.addProperty("complete", missing.isEmpty());
		result.addProperty("behaviorSourceSet", behaviorSource != null);
		result.addProperty("behaviorCompiled", behaviorCompiled);
		return result;
	}

	private List<String> missing() {
		List<String> missing = new ArrayList<>();
		if (texture == null) missing.add("texture");
		if (behaviorSource == null) missing.add("behaviorSource");
		else if (!behaviorCompiled) missing.add("behaviorCompilation");
		if (type == DynamicContentType.BLOCK) {
			if (material == null || hardness == null || preferredTool == null || requiresCorrectTool == null || blockShape == null) {
				missing.add("blockProperties");
			}
			if (redstonePower == null || comparatorPower == null) missing.add("redstone");
			if (lightLevel == null || visuallyEmissive == null) missing.add("light");
			if (particles == null) missing.add("particles");
		} else if (type == DynamicContentType.ITEM) {
			if (itemType == null || heldType == null || maxStack == null || rarity == null || foil == null
					|| enchantability == null || reach == null || placeable == null) {
				missing.add("itemProperties");
			} else if (itemType == DynamicItemType.FOOD && foodProperties == null) {
				missing.add("foodProperties");
			} else if (itemType == DynamicItemType.ITEM && placeable && placementProperties == null) {
				missing.add("placementProperties");
			} else if (itemType == DynamicItemType.TOOL && (itemTool == null || breakingPower == null || breakingSpeed == null
					|| attackDamage == null || attackSpeed == null || durability == null
					|| damagePerBlock == null || damagePerAttack == null)) {
				missing.add("toolProperties");
			}
		} else if (armorRarity == null || armorFoil == null || armorEnchantability == null || armorDefense == null
				|| armorToughness == null || armorKnockbackResistance == null || armorDurability == null) {
			missing.add("armorProperties");
		}
		return missing;
	}

	private ToolExecution requireBlock(ThrowingSupplier operation) throws Exception {
		return type == DynamicContentType.BLOCK ? operation.get()
				: ToolExecution.error("WRONG_CONTENT_TYPE", "This job is not creating a block");
	}

	private ToolExecution requireItem(ThrowingSupplier operation) throws Exception {
		return type == DynamicContentType.ITEM ? operation.get()
				: ToolExecution.error("WRONG_CONTENT_TYPE", "This job is not creating an item");
	}

	private ToolExecution requireArmor(ThrowingSupplier operation) throws Exception {
		return type == DynamicContentType.ARMOR ? operation.get()
				: ToolExecution.error("WRONG_CONTENT_TYPE", "This job is not creating armor");
	}

	private static JsonObject message(String value) {
		JsonObject result = new JsonObject();
		result.addProperty("message", value);
		return result;
	}

	private static void requireOnly(JsonObject object, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String key : object.keySet()) {
			if (!names.contains(key)) {
				throw new IllegalArgumentException("Unknown field: " + key);
			}
		}
	}

	private static JsonArray requiredArray(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonArray()) throw new IllegalArgumentException("Missing array: " + key);
		return object.getAsJsonArray(key);
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing string: " + key);
		return object.get(key).getAsString();
	}

	private static int requiredInt(JsonObject object, String key) {
		if (!object.has(key)) throw new IllegalArgumentException("Missing integer: " + key);
		return object.get(key).getAsInt();
	}

	private static float requiredFloat(JsonObject object, String key) {
		if (!object.has(key)) throw new IllegalArgumentException("Missing number: " + key);
		return object.get(key).getAsFloat();
	}

	private static double requiredDouble(JsonObject object, String key) {
		if (!object.has(key)) throw new IllegalArgumentException("Missing number: " + key);
		return object.get(key).getAsDouble();
	}

	private static boolean requiredBoolean(JsonObject object, String key) {
		if (!object.has(key)) throw new IllegalArgumentException("Missing boolean: " + key);
		return object.get(key).getAsBoolean();
	}

	private static List<String> strings(JsonArray values) {
		List<String> result = new ArrayList<>(values.size());
		for (JsonElement value : values) result.add(value.getAsString());
		return result;
	}

	private static double symmetryScore(List<String> rows) {
		int matching = 0;
		int comparisons = 0;
		for (int y = 0; y < rows.size(); y++) {
			for (int x = 0; x < rows.get(y).length(); x++) {
				char value = Character.toUpperCase(rows.get(y).charAt(x));
				if (value == Character.toUpperCase(rows.get(y).charAt(15 - x))) matching++;
				comparisons++;
				if (value == Character.toUpperCase(rows.get(15 - y).charAt(x))) matching++;
				comparisons++;
			}
		}
		return comparisons == 0 ? 0.0 : (double) matching / comparisons;
	}

	private static Set<Integer> usedColors(List<String> rows) {
		String keys = "0123456789ABCDEF";
		Set<Integer> used = new HashSet<>();
		for (String row : rows) {
			for (int index = 0; index < row.length(); index++) {
				used.add(keys.indexOf(Character.toUpperCase(row.charAt(index))));
			}
		}
		return used;
	}

	private static int[] colorCounts(List<String> rows) {
		String keys = "0123456789ABCDEF";
		int[] counts = new int[16];
		for (String row : rows) {
			for (int index = 0; index < row.length(); index++) {
				counts[keys.indexOf(Character.toUpperCase(row.charAt(index)))]++;
			}
		}
		return counts;
	}

	private static int luminanceRange(List<String> palette, Set<Integer> usedColors) {
		int minimum = 255;
		int maximum = 0;
		boolean found = false;
		for (int index : usedColors) {
			String rgba = palette.get(index);
			if (Integer.parseInt(rgba.substring(6, 8), 16) == 0) continue;
			int red = Integer.parseInt(rgba.substring(0, 2), 16);
			int green = Integer.parseInt(rgba.substring(2, 4), 16);
			int blue = Integer.parseInt(rgba.substring(4, 6), 16);
			int luminance = (int) Math.round(red * 0.2126 + green * 0.7152 + blue * 0.0722);
			minimum = Math.min(minimum, luminance);
			maximum = Math.max(maximum, luminance);
			found = true;
		}
		return found ? maximum - minimum : 0;
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 500 ? safe : safe.substring(0, 500) + "…";
	}

	@FunctionalInterface
	private interface ThrowingSupplier {
		ToolExecution get() throws Exception;
	}

	record ToolExecution(JsonObject output, GeneratedContentSpec submitted) {
		static ToolExecution success(JsonObject output, GeneratedContentSpec submitted) {
			output.addProperty("ok", true);
			return new ToolExecution(output, submitted);
		}

		static ToolExecution error(String code, String message) {
			JsonObject output = new JsonObject();
			output.addProperty("ok", false);
			output.addProperty("code", code);
			output.addProperty("message", message);
			return new ToolExecution(output, null);
		}
	}
}
