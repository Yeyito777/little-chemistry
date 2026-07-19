package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorProperties;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockModelElement;
import com.yeyito.littlechemistry.content.DynamicBlockModelFace;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBlockTexture;
import com.yeyito.littlechemistry.content.DynamicBlockUv;
import com.yeyito.littlechemistry.content.DynamicBreakingPower;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicCraftingUse;
import com.yeyito.littlechemistry.content.DynamicFoodEffect;
import com.yeyito.littlechemistry.content.DynamicFoodProperties;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicMaterial;
import com.yeyito.littlechemistry.content.DynamicParticleEmitter;
import com.yeyito.littlechemistry.content.DynamicParticleDefinition;
import com.yeyito.littlechemistry.content.DynamicParticleFrame;
import com.yeyito.littlechemistry.content.DynamicPlacedShape;
import com.yeyito.littlechemistry.content.DynamicPlacementProperties;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import com.yeyito.littlechemistry.content.DynamicTool;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.EnumMap;

final class ContentGenerationDraft {
	private final DynamicContentType type;
	private final String requestedName;
	private final DynamicArmorSlot requestedArmorSlot;
	private final int requestedOutputCount;
	private DynamicTextureSpec texture;
	private DynamicBlockModel blockModel;
	private DynamicArmorDisplayTextureSpec armorDisplayTexture;
	private DynamicArmorSlot armorDisplaySlot;
	private DynamicMaterial material;
	private Float hardness;
	private DynamicTool preferredTool;
	private Boolean requiresCorrectTool;
	private DynamicBlockShape blockShape;
	private Boolean directional;
	private Integer redstonePower;
	private Integer comparatorPower;
	private Integer lightLevel;
	private Boolean visuallyEmissive;
	private List<DynamicParticleEmitter> particles;
	private List<DynamicParticleDefinition> customParticles = List.of();
	private Integer maxStack;
	private DynamicRarity rarity;
	private String description;
	private Boolean foil;
	private DynamicItemType itemType;
	private DynamicHeldType heldType;
	private Integer enchantability;
	private DynamicFoodProperties foodProperties;
	private Boolean placeable;
	private DynamicCraftingUse craftingUse;
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
	private Boolean armorFoil;
	private Integer armorEnchantability;
	private Double armorDefense;
	private Double armorToughness;
	private Double armorKnockbackResistance;
	private Integer armorDurability;
	private DynamicArmorSlot armorSlot;
	private String behaviorSource;
	private boolean behaviorCompiled;

	ContentGenerationDraft(DynamicContentType type, String requestedName) {
		this(type, requestedName, null, 1);
	}

	ContentGenerationDraft(DynamicContentType type, String requestedName, DynamicArmorSlot requestedArmorSlot) {
		this(type, requestedName, requestedArmorSlot, 1);
	}

	ContentGenerationDraft(DynamicContentType type, String requestedName, DynamicArmorSlot requestedArmorSlot,
			int requestedOutputCount) {
		this.type = type;
		this.requestedName = requestedName;
		this.requestedArmorSlot = requestedArmorSlot;
		if (requestedOutputCount < 1 || requestedOutputCount > 64) {
			throw new IllegalArgumentException("Recipe output count must be between 1 and 64");
		}
		if (type == DynamicContentType.ARMOR && requestedOutputCount != 1) {
			throw new IllegalArgumentException("Armor recipe outputs must contain exactly one item");
		}
		this.requestedOutputCount = requestedOutputCount;
		if (type != DynamicContentType.ARMOR && requestedArmorSlot != null) {
			throw new IllegalArgumentException("Only armor generation may specify an armor slot");
		}
	}

	ToolExecution execute(String tool, JsonObject arguments) {
		try {
			if (arguments.has("_malformed")) {
				throw new IllegalArgumentException("Tool arguments were not valid JSON");
			}
			return switch (tool) {
				case "set_metadata" -> setMetadata(arguments);
				case "set_texture" -> setTexture(arguments);
				case "set_block_model" -> requireBlock(() -> setBlockModel(arguments));
				case "set_custom_particles" -> setCustomParticles(arguments);
				case "set_armor_display_texture" -> requireArmor(() -> setArmorDisplayTexture(arguments));
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

	private ToolExecution setMetadata(JsonObject arguments) {
		requireOnly(arguments, "rarity", "description");
		DynamicRarity candidateRarity = DynamicRarity.parse(requiredString(arguments, "rarity"));
		String candidateDescription = DynamicContentDefinition.normalizeDescription(
				requiredString(arguments, "description"));
		if (candidateDescription.isBlank()) {
			throw new IllegalArgumentException("description must be a short, non-empty sentence");
		}
		rarity = candidateRarity;
		description = candidateDescription;
		JsonObject details = message("Rarity and tooltip description were accepted.");
		details.addProperty("rarity", rarity.serializedName());
		details.addProperty("description", description);
		return ToolExecution.success(details, null);
	}

	private ToolExecution setTexture(JsonObject arguments) {
		if (type == DynamicContentType.BLOCK) {
			return ToolExecution.error("WRONG_TEXTURE_TOOL", "Blocks define one or more textures through set_block_model");
		}
		requireOnly(arguments, "palette", "rows");
		List<String> palette = strings(requiredArray(arguments, "palette"));
		List<String> rows = strings(requiredArray(arguments, "rows"));
		DynamicTextureSpec candidate = new DynamicTextureSpec(palette, rows);
		candidate.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
		Set<Integer> usedColors = usedColors(rows);
		int[] colorCounts = colorCounts(rows);
		int luminanceRange = luminanceRange(palette, usedColors);
		candidate.requireBinaryAlpha();
		int opaquePixels = 0;
		int usedOpaqueColors = 0;
		for (int colorIndex = 0; colorIndex < palette.size(); colorIndex++) {
			if (palette.get(colorIndex).regionMatches(true, 6, "FF", 0, 2)) {
				opaquePixels += colorCounts[colorIndex];
				if (colorCounts[colorIndex] > 0) usedOpaqueColors++;
			}
		}
		if (opaquePixels < 12 || usedOpaqueColors < 2) {
			throw new IllegalArgumentException("Item or armor texture is effectively invisible; draw at least 12 visible pixels using two opaque colors");
		}
		if (usedColors.size() < 3 || luminanceRange < 40) {
			throw new IllegalArgumentException("Item or armor texture needs at least three used colors and stronger readable contrast");
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

	private ToolExecution setBlockModel(JsonObject arguments) throws Exception {
		if (blockShape == null) {
			return ToolExecution.error("MISSING_BLOCK_SHAPE", "Call set_block_properties before set_block_model");
		}
		requireOnly(arguments, "textures", "particleTexture", "faces", "elements");
		JsonArray encodedTextures = requiredArray(arguments, "textures");
		if (encodedTextures.isEmpty() || encodedTextures.size() > DynamicBlockModel.MAX_TEXTURES) {
			throw new IllegalArgumentException("Block models require 1-12 textures");
		}
		List<DynamicBlockTexture> textures = new ArrayList<>();
		int totalPixels = 0;
		for (JsonElement element : encodedTextures) {
			if (!(element instanceof JsonObject encoded)) throw new IllegalArgumentException("Every block texture must be an object");
			requireOnly(encoded, "id", "width", "height", "palette", "rows");
			int width = requiredInt(encoded, "width");
			int height = requiredInt(encoded, "height");
			DynamicTextureSpec specification = new DynamicTextureSpec(
					strings(requiredArray(encoded, "palette")), strings(requiredArray(encoded, "rows")));
			specification.requireDimensions(width, height);
			totalPixels += width * height;
			if (totalPixels > 16_384) throw new IllegalArgumentException("Block model textures exceed the 16,384-pixel budget");
			validateBlockModelTexture(specification);
			byte[] png = specification.renderPng();
			textures.add(new DynamicBlockTexture(requiredString(encoded, "id"),
					DynamicTextureAsset.sha256(png), specification));
		}

		Map<Direction, DynamicBlockModelFace> defaultFaces = parseBlockFaces(
				requiredObject(arguments, "faces"), Map.of(), true);
		JsonArray encodedElements = requiredArray(arguments, "elements");
		if (blockShape == DynamicBlockShape.CUSTOM && encodedElements.isEmpty()) {
			throw new IllegalArgumentException("shape=custom requires at least one cuboid element");
		}
		if (blockShape != DynamicBlockShape.CUSTOM && !encodedElements.isEmpty()) {
			throw new IllegalArgumentException("Only shape=custom accepts cuboid elements");
		}
		if (encodedElements.size() > DynamicBlockModel.MAX_ELEMENTS) {
			throw new IllegalArgumentException("Custom block models may have at most 24 cuboid elements");
		}
		List<DynamicBlockModelElement> elements = new ArrayList<>();
		for (JsonElement element : encodedElements) {
			if (!(element instanceof JsonObject encoded)) throw new IllegalArgumentException("Every custom block element must be an object");
			requireOnly(encoded, "from", "to", "collision", "faces");
			float[] from = coordinates(requiredArray(encoded, "from"), 3);
			float[] to = coordinates(requiredArray(encoded, "to"), 3);
			Map<Direction, DynamicBlockModelFace> faces = parseBlockFaces(
					requiredObject(encoded, "faces"), defaultFaces, false);
			elements.add(new DynamicBlockModelElement(from[0], from[1], from[2], to[0], to[1], to[2],
					requiredBoolean(encoded, "collision"), faces));
		}
		DynamicBlockModel candidate = new DynamicBlockModel(textures, requiredString(arguments, "particleTexture"),
				defaultFaces, elements);
		candidate.validateFor(blockShape);
		validateCutoutModel(candidate, blockShape);
		blockModel = candidate;
		texture = candidate.particleTextureAsset().texture();
		JsonObject details = message("The block model and all of its textures were accepted.");
		details.addProperty("textureCount", textures.size());
		details.addProperty("totalTexturePixels", totalPixels);
		details.addProperty("elementCount", elements.size());
		details.addProperty("shape", blockShape.serializedName());
		return ToolExecution.success(details, null);
	}

	private ToolExecution setArmorDisplayTexture(JsonObject arguments) {
		requireOnly(arguments, "slot", "palette", "rows");
		DynamicArmorSlot slot = DynamicArmorSlot.parse(requiredString(arguments, "slot"));
		if (requestedArmorSlot != null && slot != requestedArmorSlot) {
			throw new IllegalArgumentException("Armor display texture slot must be " + requestedArmorSlot.serializedName());
		}
		if (armorSlot != null && slot != armorSlot) {
			throw new IllegalArgumentException("Armor display texture slot must match the armor properties slot "
					+ armorSlot.serializedName());
		}
		List<String> palette = strings(requiredArray(arguments, "palette"));
		List<String> rows = strings(requiredArray(arguments, "rows"));
		DynamicArmorDisplayTextureSpec candidate = new DynamicArmorDisplayTextureSpec(palette, rows);
		Set<Integer> usedColors = usedColors(rows);
		int[] counts = colorCounts(rows);
		int opaquePixels = 0;
		int transparentPixels = 0;
		for (int countIndex = 0; countIndex < palette.size(); countIndex++) {
			int count = counts[countIndex];
			if (palette.get(countIndex).regionMatches(true, 6, "00", 0, 2)) {
				transparentPixels += count;
			} else {
				opaquePixels += count;
			}
		}
		if (opaquePixels == 0 || transparentPixels == 0) {
			throw new IllegalArgumentException("Armor display texture must contain both visible armor pixels and transparent unused UV space");
		}
		if (usedColors.size() < 3 || luminanceRange(palette, usedColors) < 32) {
			throw new IllegalArgumentException("Armor display texture needs at least three used colors and readable shading contrast");
		}
		int relevantOpaquePixels = relevantOpaquePixels(candidate, slot);
		if (relevantOpaquePixels < 16) {
			throw new IllegalArgumentException("Armor display texture has too few visible pixels in the "
					+ slot.serializedName() + " UV islands");
		}
		armorDisplayTexture = candidate;
		armorDisplaySlot = slot;
		JsonObject details = new JsonObject();
		details.addProperty("paletteColors", palette.size());
		details.addProperty("paletteColorsUsed", usedColors.size());
		details.addProperty("opaquePixels", opaquePixels);
		details.addProperty("transparentPixels", transparentPixels);
		details.addProperty("slot", slot.serializedName());
		details.addProperty("relevantOpaquePixels", relevantOpaquePixels);
		details.addProperty("message", "The separate 64x32 equipped-armor display texture was accepted.");
		return ToolExecution.success(details, null);
	}

	private ToolExecution setBlockProperties(JsonObject arguments) {
		requireOnly(arguments, "material", "hardness", "preferredTool", "requiresCorrectTool", "shape", "directional");
		DynamicMaterial candidateMaterial = DynamicMaterial.parse(requiredString(arguments, "material"));
		float candidateHardness = requiredFloat(arguments, "hardness");
		DynamicTool candidatePreferredTool = DynamicTool.parse(requiredString(arguments, "preferredTool"));
		boolean candidateRequiresCorrectTool = requiredBoolean(arguments, "requiresCorrectTool");
		DynamicBlockShape candidateShape = DynamicBlockShape.parse(requiredString(arguments, "shape"));
		boolean candidateDirectional = requiredBoolean(arguments, "directional");
		if (!Float.isFinite(candidateHardness) || candidateHardness < 0.0F || candidateHardness > 50.0F) {
			throw new IllegalArgumentException("hardness must be between 0 and 50");
		}
		boolean clearedModel = false;
		if (blockModel != null) {
			try {
				blockModel.validateFor(candidateShape);
				validateCutoutModel(blockModel, candidateShape);
			} catch (IllegalArgumentException incompatible) {
				blockModel = null;
				texture = null;
				clearedModel = true;
			}
		}
		material = candidateMaterial;
		hardness = candidateHardness;
		preferredTool = candidatePreferredTool;
		requiresCorrectTool = candidateRequiresCorrectTool;
		blockShape = candidateShape;
		directional = candidateDirectional;
		JsonObject details = message(clearedModel
				? "Block physical properties were accepted. The incompatible previous model was cleared; call set_block_model again."
				: "Block physical properties were accepted.");
		details.addProperty("modelCleared", clearedModel);
		return ToolExecution.success(details, null);
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
			requireOnly(emitter, "particle", "chancePerTick", "count", "velocity", "region");
			String region = requiredString(emitter, "region");
			if (!region.equals("top") && !region.equals("all")) {
				throw new IllegalArgumentException("Particle region must be 'top' or 'all'");
			}
			decoded.add(new DynamicParticleEmitter(
					requiredString(emitter, "particle"),
					requiredDouble(emitter, "chancePerTick"),
					requiredInt(emitter, "count"),
					requiredDouble(emitter, "velocity"),
					region.equals("top")
			));
		}
		particles = List.copyOf(decoded);
		return ToolExecution.success(message("Block particle properties were accepted."), null);
	}

	private ToolExecution setCustomParticles(JsonObject arguments) throws Exception {
		requireOnly(arguments, "particles");
		JsonArray encodedParticles = requiredArray(arguments, "particles");
		if (encodedParticles.size() > DynamicParticleDefinition.MAX_DEFINITIONS) {
			throw new IllegalArgumentException("At most " + DynamicParticleDefinition.MAX_DEFINITIONS
					+ " custom particles are allowed");
		}
		List<DynamicParticleDefinition> decoded = new ArrayList<>();
		for (JsonElement element : encodedParticles) {
			if (!(element instanceof JsonObject particle)) {
				throw new IllegalArgumentException("Every custom particle must be an object");
			}
			requireOnly(particle, "id", "frames", "frameTicks", "loop", "lifetimeTicks",
					"startSize", "endSize", "startColor", "endColor", "gravity", "friction",
					"collision", "emissive", "spin");
			List<DynamicParticleFrame> frames = new ArrayList<>();
			for (JsonElement frameElement : requiredArray(particle, "frames")) {
				if (!(frameElement instanceof JsonObject frame)) {
					throw new IllegalArgumentException("Every custom particle frame must be an object");
				}
				requireOnly(frame, "palette", "rows");
				DynamicTextureSpec texture = new DynamicTextureSpec(
						strings(requiredArray(frame, "palette")), strings(requiredArray(frame, "rows")));
				frames.add(new DynamicParticleFrame(
						DynamicTextureAsset.sha256(texture.renderPng()), texture));
			}
			decoded.add(new DynamicParticleDefinition(
					requiredString(particle, "id"),
					frames,
					requiredInt(particle, "frameTicks"),
					requiredBoolean(particle, "loop"),
					requiredInt(particle, "lifetimeTicks"),
					(float) requiredDouble(particle, "startSize"),
					(float) requiredDouble(particle, "endSize"),
					requiredString(particle, "startColor"),
					requiredString(particle, "endColor"),
					(float) requiredDouble(particle, "gravity"),
					(float) requiredDouble(particle, "friction"),
					requiredBoolean(particle, "collision"),
					requiredBoolean(particle, "emissive"),
					(float) requiredDouble(particle, "spin")
			));
		}
		Set<String> idsSeen = new HashSet<>();
		for (DynamicParticleDefinition particle : decoded) {
			if (!idsSeen.add(particle.id())) {
				throw new IllegalArgumentException("Duplicate custom particle ID: " + particle.id());
			}
		}
		customParticles = List.copyOf(decoded);
		JsonObject details = message("Custom particle definitions were accepted.");
		details.addProperty("particleCount", customParticles.size());
		JsonArray ids = new JsonArray();
		customParticles.forEach(particle -> ids.add(particle.id()));
		details.add("particleIds", ids);
		return ToolExecution.success(details, null);
	}

	private ToolExecution setItemProperties(JsonObject arguments) {
		requireOnly(arguments, "itemType", "heldType", "maxStack", "foil", "enchantability", "reach", "placeable", "craftingUse");
		DynamicItemType candidateItemType = DynamicItemType.parse(requiredString(arguments, "itemType"));
		DynamicHeldType candidateHeldType = DynamicHeldType.parse(requiredString(arguments, "heldType"));
		int candidateMaxStack = requiredInt(arguments, "maxStack");
		boolean candidateFoil = requiredBoolean(arguments, "foil");
		int candidateEnchantability = requiredInt(arguments, "enchantability");
		double candidateReach = requiredDouble(arguments, "reach");
		boolean candidatePlaceable = requiredBoolean(arguments, "placeable");
		DynamicCraftingUse candidateCraftingUse = DynamicCraftingUse.parse(requiredString(arguments, "craftingUse"));
		if (candidateMaxStack < 1 || candidateMaxStack > 64) {
			throw new IllegalArgumentException("maxStack must be between 1 and 64");
		}
		if (candidateEnchantability < 0 || candidateEnchantability > 255) {
			throw new IllegalArgumentException("enchantability must be between 0 and 255");
		}
		if (!Double.isFinite(candidateReach) || candidateReach < 0 || candidateReach > 16) {
			throw new IllegalArgumentException("reach must be between 0 and 16");
		}
		if (candidateMaxStack < requestedOutputCount) {
			throw new IllegalArgumentException("maxStack must be at least the requested recipe output count of "
					+ requestedOutputCount);
		}
		if (candidateItemType != DynamicItemType.ITEM && candidatePlaceable) {
			throw new IllegalArgumentException("Only ordinary items can be placeable");
		}
		if (candidateCraftingUse == DynamicCraftingUse.DAMAGE && candidateItemType != DynamicItemType.TOOL) {
			throw new IllegalArgumentException("craftingUse=damage requires itemType=tool");
		}
		if (candidateItemType == DynamicItemType.ITEM) {
			new DynamicItemProperties(candidateItemType, candidateHeldType, candidateMaxStack,
					rarity == null ? net.minecraft.world.item.Rarity.COMMON : rarity.vanillaRarity(),
					candidateFoil, candidateEnchantability, candidateReach,
					DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0,
					null, null, candidateCraftingUse);
		} else if (candidateItemType == DynamicItemType.TOOL && candidateMaxStack != 1) {
			throw new IllegalArgumentException("Tools must use maxStack 1");
		}
		itemType = candidateItemType;
		heldType = candidateHeldType;
		maxStack = candidateMaxStack;
		foil = candidateFoil;
		enchantability = candidateEnchantability;
		reach = candidateReach;
		placeable = candidatePlaceable;
		craftingUse = candidateCraftingUse;
		if (itemType != DynamicItemType.FOOD) foodProperties = null;
		if (!placeable) placementProperties = null;
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
		new DynamicItemProperties(itemType, heldType, maxStack,
				rarity == null ? net.minecraft.world.item.Rarity.COMMON : rarity.vanillaRarity(),
				foil, enchantability, reach,
				itemTool, breakingPower, breakingSpeed, attackDamage, attackSpeed,
				durability, damagePerBlock, damagePerAttack, null, null, craftingUse);
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
		requireOnly(arguments, "shape", "supportProfile", "supports", "lightLevel", "visuallyEmissive");
		DynamicPlacedShape shape = DynamicPlacedShape.parse(requiredString(arguments, "shape"));
		String supportProfile = requiredString(arguments, "supportProfile");
		if (!supportProfile.equals("overworld_vegetation") && !supportProfile.equals("explicit")) {
			throw new IllegalArgumentException("supportProfile must be overworld_vegetation or explicit");
		}
		if (supportProfile.equals("overworld_vegetation") && shape != DynamicPlacedShape.CROSS) {
			throw new IllegalArgumentException("overworld_vegetation support requires cross geometry");
		}
		List<String> supports = resolvePlacementSupports(
				supportProfile, strings(requiredArray(arguments, "supports")));
		placementProperties = new DynamicPlacementProperties(
				shape,
				supports,
				requiredInt(arguments, "lightLevel"),
				requiredBoolean(arguments, "visuallyEmissive")
		);
		JsonObject details = message("Item placement properties were accepted.");
		details.addProperty("supportProfile", supportProfile);
		JsonArray resolvedSupports = new JsonArray();
		placementProperties.supports().forEach(resolvedSupports::add);
		details.add("resolvedSupports", resolvedSupports);
		return ToolExecution.success(details, null);
	}

	static List<String> resolvePlacementSupports(String supportProfile, List<String> suppliedSupports) {
		List<String> supports = new ArrayList<>(suppliedSupports);
		if (supportProfile.equals("overworld_vegetation")
				&& !supports.contains("#minecraft:supports_vegetation")) {
			supports.addFirst("#minecraft:supports_vegetation");
		}
		return List.copyOf(supports);
	}

	private ToolExecution setArmorProperties(JsonObject arguments) {
		requireOnly(arguments, "slot", "foil", "enchantability", "defense", "toughness",
				"knockbackResistance", "durability");
		DynamicArmorSlot slot = DynamicArmorSlot.parse(requiredString(arguments, "slot"));
		if (requestedArmorSlot != null && slot != requestedArmorSlot) {
			throw new IllegalArgumentException("Armor slot must be " + requestedArmorSlot.serializedName());
		}
		if (armorDisplaySlot != null && slot != armorDisplaySlot) {
			throw new IllegalArgumentException("Armor slot must match the display texture slot "
					+ armorDisplaySlot.serializedName());
		}
		boolean candidateFoil = requiredBoolean(arguments, "foil");
		int candidateEnchantability = requiredInt(arguments, "enchantability");
		double candidateDefense = requiredDouble(arguments, "defense");
		double candidateToughness = requiredDouble(arguments, "toughness");
		double candidateKnockbackResistance = requiredDouble(arguments, "knockbackResistance");
		int candidateDurability = requiredInt(arguments, "durability");
		new DynamicArmorProperties(slot,
				rarity == null ? net.minecraft.world.item.Rarity.COMMON : rarity.vanillaRarity(),
				candidateFoil, candidateEnchantability,
				candidateDefense, candidateToughness, candidateKnockbackResistance, candidateDurability);
		armorSlot = slot;
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
		details.addProperty("required", true);
		details.addProperty("requiredClass", "public final class GeneratedBehaviorImpl implements DynamicBehavior");
		details.addProperty("constructor", "public GeneratedBehaviorImpl()");
		details.addProperty("implementationScope", "DynamicBehavior is an empty marker. Add only the callback capability interfaces the content actually needs; each selected interface has one abstract method and no default implementation.");
		JsonArray capabilities = new JsonArray();
		for (DynamicBehaviorCapability capability : DynamicBehaviorCapability.values()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("interface", capability.interfaceName());
			encoded.addProperty("callback", capability.callbackName());
			capabilities.add(encoded);
		}
		details.add("capabilities", capabilities);
		details.addProperty("inspection", "Call inspect_java_class on a capability in com.yeyito.littlechemistry.behavior for its exact method signature, and inspect its context record when applicable.");
		details.addProperty("airContext", "context.level(): ServerLevel; context.player(): ServerPlayer; context.hand(): InteractionHand; context.stack(): ItemStack; context.definition(): DynamicContentDefinition");
		details.addProperty("blockContext", "All air fields plus context.clickedPos(): BlockPos; context.clickedFace(): Direction; context.clickLocation(): Vec3; context.adjacentPos(): BlockPos");
		details.addProperty("semantics", "Callbacks run only on the authoritative server. PASS continues normal placement, eating, equipping, or block-item behavior; SUCCESS or CONSUME handles an interaction, FAIL rejects it, and finishUsing must return an ItemStack.");
		details.addProperty("customParticleApi", "Use com.yeyito.littlechemistry.particle.DynamicParticles.spawn(ServerLevel, DynamicContentDefinition, String particleId, double x, double y, double z, double velocityX, double velocityY, double velocityZ) for one directionally moving particle. The burst overload adds int count, double spreadX, spreadY, spreadZ, and double speed, matching ServerLevel.sendParticles semantics. Pass a string-literal local ID declared by set_custom_particles.");
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
		validateBehaviorParticleReferences();
		GeneratedContentSpec generated;
		if (type == DynamicContentType.BLOCK) {
			generated = new GeneratedContentSpec(
					texture,
					new DynamicBlockProperties(material, hardness, preferredTool, requiresCorrectTool,
							blockShape, directional, rarity.vanillaRarity(), redstonePower, comparatorPower, lightLevel,
							visuallyEmissive, particles),
					null,
					null,
					null,
					behaviorSource,
					blockModel,
					rarity,
					description,
					customParticles
			);
		} else if (type == DynamicContentType.ITEM) {
			DynamicItemProperties item = itemType == DynamicItemType.TOOL
					? new DynamicItemProperties(itemType, heldType, maxStack, rarity.vanillaRarity(), foil, enchantability, reach,
							itemTool, breakingPower, breakingSpeed, attackDamage, attackSpeed,
							durability, damagePerBlock, damagePerAttack, null, null, craftingUse)
					: new DynamicItemProperties(itemType, heldType, maxStack, rarity.vanillaRarity(), foil, enchantability, reach,
							DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0,
							itemType == DynamicItemType.FOOD ? foodProperties : null,
							itemType == DynamicItemType.ITEM && Boolean.TRUE.equals(placeable) ? placementProperties : null,
							craftingUse);
			generated = new GeneratedContentSpec(texture, null, item, null, null, behaviorSource, null,
					rarity, description, customParticles);
		} else {
			generated = new GeneratedContentSpec(texture, null, null,
					new DynamicArmorProperties(armorSlot, rarity.vanillaRarity(), armorFoil, armorEnchantability,
							armorDefense, armorToughness, armorKnockbackResistance, armorDurability),
					armorDisplayTexture,
					behaviorSource, null, rarity, description, customParticles);
		}
		JsonObject details = message("The draft passed validation and was submitted.");
		details.addProperty("submitted", true);
		return ToolExecution.success(details, generated);
	}

	private void validateBehaviorParticleReferences() {
		Set<String> available = customParticles.stream().map(DynamicParticleDefinition::id)
				.collect(java.util.stream.Collectors.toSet());
		for (String referenced : DynamicBehaviorSource.referencedCustomParticleIds(behaviorSource)) {
			if (!available.contains(referenced)) {
				throw new IllegalArgumentException("Behavior references undefined custom particle: " + referenced);
			}
		}
	}

	private JsonObject inspect() {
		JsonObject result = new JsonObject();
		result.addProperty("ok", true);
		result.addProperty("kind", type.serializedName());
		result.addProperty("requestedName", requestedName);
		result.addProperty("requestedOutputCount", requestedOutputCount);
		if (requestedArmorSlot != null) result.addProperty("requestedArmorSlot", requestedArmorSlot.serializedName());
		JsonArray missing = new JsonArray();
		missing().forEach(missing::add);
		result.add("missing", missing);
		result.addProperty("complete", missing.isEmpty());
		result.addProperty("metadataSet", rarity != null && description != null);
		if (rarity != null) result.addProperty("rarity", rarity.serializedName());
		if (description != null) result.addProperty("description", description);
		result.addProperty("behaviorRequired", true);
		result.addProperty("behaviorSourceSet", behaviorSource != null);
		result.addProperty("behaviorCompiled", behaviorCompiled);
		result.addProperty("behaviorStatus", behaviorSource == null ? "none" : behaviorCompiled ? "compiled" : "needs_compilation");
		result.addProperty("armorDisplayTextureSet", armorDisplayTexture != null);
		if (armorDisplaySlot != null) result.addProperty("armorDisplayTextureSlot", armorDisplaySlot.serializedName());
		result.addProperty("blockModelSet", blockModel != null);
		result.addProperty("customParticleCount", customParticles.size());
		JsonArray customParticleIds = new JsonArray();
		customParticles.forEach(particle -> customParticleIds.add(particle.id()));
		result.add("customParticleIds", customParticleIds);
		if (directional != null) result.addProperty("directional", directional);
		if (blockModel != null) {
			result.addProperty("blockModelTextureCount", blockModel.textures().size());
			result.addProperty("blockModelElementCount", blockModel.elements().size());
		}
		return result;
	}

	private List<String> missing() {
		List<String> missing = new ArrayList<>();
		if (rarity == null || description == null) missing.add("metadata");
		if (type == DynamicContentType.BLOCK) {
			if (blockModel == null) missing.add("blockModel");
		} else if (texture == null) missing.add("texture");
		if (type == DynamicContentType.ARMOR && armorDisplayTexture == null) missing.add("armorDisplayTexture");
		if (behaviorSource == null) missing.add("behaviorSource");
		else if (!behaviorCompiled) missing.add("behaviorCompilation");
		if (type == DynamicContentType.BLOCK) {
			if (material == null || hardness == null || preferredTool == null || requiresCorrectTool == null
					|| blockShape == null || directional == null) {
				missing.add("blockProperties");
			}
			if (redstonePower == null || comparatorPower == null) missing.add("redstone");
			if (lightLevel == null || visuallyEmissive == null) missing.add("light");
			if (particles == null) missing.add("particles");
		} else if (type == DynamicContentType.ITEM) {
			if (itemType == null || heldType == null || maxStack == null || foil == null
					|| enchantability == null || reach == null || placeable == null || craftingUse == null) {
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
		} else if (armorSlot == null || armorFoil == null || armorEnchantability == null || armorDefense == null
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

	private static JsonObject requiredObject(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonObject()) throw new IllegalArgumentException("Missing object: " + key);
		return object.getAsJsonObject(key);
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
			int width = rows.get(y).length();
			for (int x = 0; x < width; x++) {
				char value = Character.toUpperCase(rows.get(y).charAt(x));
				if (value == Character.toUpperCase(rows.get(y).charAt(width - 1 - x))) matching++;
				comparisons++;
				if (value == Character.toUpperCase(rows.get(rows.size() - 1 - y).charAt(x))) matching++;
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

	static void validateBlockTextureAlpha(DynamicTextureSpec texture, DynamicBlockShape shape) {
		if (shape != null && shape != DynamicBlockShape.STAR && shape != DynamicBlockShape.CROSS
				&& shape != DynamicBlockShape.TORCH && shape != DynamicBlockShape.CUSTOM) {
			texture.requireOpaque();
			return;
		}
		texture.requireBinaryAlpha();
		int[] counts = colorCounts(texture.rows());
		int opaquePixels = 0;
		int transparentPixels = 0;
		int opaqueColors = 0;
		for (int colorIndex = 0; colorIndex < texture.palette().size(); colorIndex++) {
			if (texture.palette().get(colorIndex).regionMatches(true, 6, "FF", 0, 2)) {
				opaquePixels += counts[colorIndex];
				if (counts[colorIndex] > 0) opaqueColors++;
			} else {
				transparentPixels += counts[colorIndex];
			}
		}
		if (opaquePixels < 16 || opaqueColors < 3) {
			throw new IllegalArgumentException("A star-mesh block texture needs at least 16 visible pixels using three opaque colors");
		}
		if ((shape == DynamicBlockShape.STAR || shape == DynamicBlockShape.CROSS) && transparentPixels < 16) {
			throw new IllegalArgumentException("A star-mesh block texture needs at least 16 transparent pixels around its artwork");
		}
	}

	private static void validateBlockModelTexture(DynamicTextureSpec texture) {
		Set<Integer> used = usedColors(texture.rows());
		int[] counts = colorCounts(texture.rows());
		int opaquePixels = 0;
		for (int index = 0; index < texture.palette().size(); index++) {
			if (texture.palette().get(index).regionMatches(true, 6, "FF", 0, 2)) opaquePixels += counts[index];
		}
		if (opaquePixels == 0) throw new IllegalArgumentException("Block model textures must contain at least one visible pixel");
		if (texture.width() * texture.height() > 1 && used.size() < 2) {
			throw new IllegalArgumentException("Block model textures with multiple pixels need at least two used colors");
		}
		if (used.size() > 1 && luminanceRange(texture.palette(), used) < 20) {
			throw new IllegalArgumentException("Block model texture contrast is too low");
		}
	}

	private static void validateCutoutModel(DynamicBlockModel model, DynamicBlockShape shape) {
		if (shape != DynamicBlockShape.STAR && shape != DynamicBlockShape.CROSS) return;
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			DynamicTextureSpec texture = model.texture(model.faces().get(direction).texture()).texture();
			if (!hasTransparentPixel(texture)) {
				throw new IllegalArgumentException(shape.serializedName()
						+ " model textures used on crossed planes need transparent background pixels");
			}
		}
	}

	private static boolean hasTransparentPixel(DynamicTextureSpec texture) {
		int[] counts = colorCounts(texture.rows());
		for (int index = 0; index < texture.palette().size(); index++) {
			if (counts[index] > 0 && texture.palette().get(index).regionMatches(true, 6, "00", 0, 2)) return true;
		}
		return false;
	}

	private static Map<Direction, DynamicBlockModelFace> parseBlockFaces(JsonObject encoded,
			Map<Direction, DynamicBlockModelFace> inherited, boolean requireAll) {
		requireOnly(encoded, "down", "up", "north", "south", "west", "east");
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		faces.putAll(inherited);
		for (var entry : encoded.entrySet()) {
			Direction direction = Direction.byName(entry.getKey());
			if (direction == null || !entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException("Invalid block model face: " + entry.getKey());
			}
			JsonObject face = entry.getValue().getAsJsonObject();
			requireOnly(face, "texture", "uv");
			DynamicBlockUv uv = face.has("uv")
					? uv(coordinates(requiredArray(face, "uv"), 4)) : null;
			faces.put(direction, new DynamicBlockModelFace(requiredString(face, "texture"), uv));
		}
		if (requireAll) {
			for (Direction direction : Direction.values()) {
				if (!faces.containsKey(direction)) throw new IllegalArgumentException("Missing default face: " + direction.getSerializedName());
			}
		}
		return faces;
	}

	private static DynamicBlockUv uv(float[] values) {
		return new DynamicBlockUv(values[0], values[1], values[2], values[3]);
	}

	private static float[] coordinates(JsonArray encoded, int expected) {
		if (encoded.size() != expected) throw new IllegalArgumentException("Expected " + expected + " coordinates");
		float[] values = new float[expected];
		for (int index = 0; index < expected; index++) values[index] = encoded.get(index).getAsFloat();
		return values;
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

	private static int relevantOpaquePixels(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot) {
		return switch (slot) {
			case HEAD -> opaquePixels(texture, 0, 0, 32, 16);
			case CHEST -> opaquePixels(texture, 16, 16, 24, 16)
					+ opaquePixels(texture, 40, 16, 16, 16);
			case LEGGINGS -> opaquePixels(texture, 0, 16, 40, 16);
			case BOOTS -> opaquePixels(texture, 0, 16, 16, 16);
		};
	}

	private static int opaquePixels(DynamicArmorDisplayTextureSpec texture, int left, int top, int width, int height) {
		String keys = "0123456789ABCDEF";
		int count = 0;
		for (int y = top; y < top + height; y++) {
			String row = texture.rows().get(y);
			for (int x = left; x < left + width; x++) {
				String rgba = texture.palette().get(keys.indexOf(Character.toUpperCase(row.charAt(x))));
				if (!rgba.regionMatches(true, 6, "00", 0, 2)) count++;
			}
		}
		return count;
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
