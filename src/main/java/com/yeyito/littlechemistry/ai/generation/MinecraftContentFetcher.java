package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicEntityVisualProfile;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MinecraftContentFetcher {
	private static final String BLOCK_PREFIX = "assets/minecraft/textures/block/";
	private static final String ITEM_PREFIX = "assets/minecraft/textures/item/";
	private static final String ARMOR_DISPLAY_PREFIX = "assets/minecraft/textures/entity/equipment/";
	private static final String ENTITY_TEXTURE_PREFIX = "assets/minecraft/textures/entity/";
	private static final int RETURNED_REFERENCES = 6;
	private static final List<String> BLOCK_TEXTURE_SUFFIXES = List.of(
			"_front_on", "_side_on", "_top_on", "_bottom_on", "_front", "_side", "_top", "_bottom", "_back", "_on"
	);
	private static volatile List<TextureEntry> textureIndex;
	private static volatile List<ArmorDisplayTextureEntry> armorDisplayTextureIndex;
	private static final List<EntityVisualReference> ENTITY_VISUAL_REFERENCES = List.of(
			visual("zombie", "minecraft:zombie", DynamicEntityVisualProfile.ZOMBIE, "zombie/zombie"),
			visual("husk", "minecraft:husk", DynamicEntityVisualProfile.ZOMBIE, "zombie/husk"),
			visual("drowned", "minecraft:drowned", DynamicEntityVisualProfile.ZOMBIE, "zombie/drowned"),
			visual("giant zombie", "minecraft:giant", DynamicEntityVisualProfile.ZOMBIE, "zombie/zombie"),
			visual("skeleton", "minecraft:skeleton", DynamicEntityVisualProfile.SKELETON, "skeleton/skeleton"),
			visual("stray", "minecraft:stray", DynamicEntityVisualProfile.SKELETON, "skeleton/stray"),
			visual("bogged", "minecraft:bogged", DynamicEntityVisualProfile.SKELETON, "skeleton/bogged"),
			visual("wither skeleton", "minecraft:wither_skeleton", DynamicEntityVisualProfile.SKELETON, "skeleton/wither_skeleton"),
			visual("enderman", "minecraft:enderman", DynamicEntityVisualProfile.ENDERMAN, "enderman/enderman"),
			visual("temperate cow", "minecraft:cow", DynamicEntityVisualProfile.COW, "cow/cow_temperate"),
			visual("red mooshroom", "minecraft:mooshroom", DynamicEntityVisualProfile.COW, "cow/mooshroom_red"),
			visual("brown mooshroom", "minecraft:mooshroom", DynamicEntityVisualProfile.COW, "cow/mooshroom_brown"),
			visual("temperate pig", "minecraft:pig", DynamicEntityVisualProfile.PIG, "pig/pig_temperate"),
			visual("spider", "minecraft:spider", DynamicEntityVisualProfile.SPIDER, "spider/spider"),
			visual("cave spider", "minecraft:cave_spider", DynamicEntityVisualProfile.SPIDER, "spider/cave_spider"),
			visual("creeper", "minecraft:creeper", DynamicEntityVisualProfile.CREEPER, "creeper/creeper"),
			visual("blaze", "minecraft:blaze", DynamicEntityVisualProfile.BLAZE, "blaze/blaze"),
			visual("cod", "minecraft:cod", DynamicEntityVisualProfile.COD, "fish/cod")
	);

	private MinecraftContentFetcher() {
	}

	static ContentGenerationDraft.ToolExecution fetch(JsonObject arguments) {
		return fetch(arguments, false);
	}

	static ContentGenerationDraft.ToolExecution fetchTexture(JsonObject arguments) {
		return fetch(arguments, true);
	}

	static ContentGenerationDraft.ToolExecution fetchArmorDisplayTexture(JsonObject arguments) {
		try {
			if (arguments.has("_malformed")) throw new IllegalArgumentException("Tool arguments were not valid JSON");
			requireOnly(arguments, "query", "slot");
			String query = requiredString(arguments, "query").trim().toLowerCase(Locale.ROOT);
			String slot = requiredString(arguments, "slot").trim().toLowerCase(Locale.ROOT);
			if (query.isEmpty() || query.length() > 80) {
				throw new IllegalArgumentException("query must contain between 1 and 80 characters");
			}
			if (!Set.of("head", "chest", "leggings", "boots").contains(slot)) {
				throw new IllegalArgumentException("slot must be head, chest, leggings, or boots");
			}
			String layer = slot.equals("leggings") ? "humanoid_leggings" : "humanoid";
			List<ScoredArmorDisplayTexture> matches = matchingArmorDisplayTextures(query, layer);
			if (matches.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error(
						"NO_ARMOR_DISPLAY_TEXTURE_MATCH",
						"No installed vanilla " + layer + " equipment texture matched '" + query
								+ "'. Try a material such as iron, copper, diamond, gold, leather, chainmail, or netherite."
				);
			}

			JsonArray references = new JsonArray();
			List<String> failures = new ArrayList<>();
			for (ScoredArmorDisplayTexture match : matches) {
				try {
					references.add(armorDisplayTexturePreview(match.texture()));
				} catch (Exception error) {
					failures.add(match.texture().id() + ": " + safeMessage(error));
				}
			}
			if (references.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error(
						"ARMOR_DISPLAY_TEXTURE_READ_FAILED",
						"Matching vanilla armor display textures were found but could not be read: " + String.join("; ", failures)
				);
			}

			JsonObject result = new JsonObject();
			result.addProperty("query", query);
			result.addProperty("slot", slot);
			result.addProperty("targetLayer", layer);
			result.addProperty("textureFormat", "little_chemistry:indexed_rgba_64x32");
			result.addProperty("setArmorDisplayTextureCompatible", true);
			result.add("authoringInstructions", armorDisplayTextureInstructions(layer));
			result.add("references", references);
			if (!failures.isEmpty()) {
				JsonArray warnings = new JsonArray();
				failures.forEach(warnings::add);
				result.add("warnings", warnings);
			}
			LittleChemistry.LOGGER.info("Fetched vanilla Minecraft armor display textures for '{}' ({}): {}",
					query, layer, matches.stream().map(match -> match.texture().id()).toList());
			return ContentGenerationDraft.ToolExecution.success(result, null);
		} catch (IllegalArgumentException error) {
			return ContentGenerationDraft.ToolExecution.error("INVALID_ARGUMENT", safeMessage(error));
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("TOOL_FAILURE", safeMessage(error));
		}
	}

	static ContentGenerationDraft.ToolExecution fetchEntity(JsonObject arguments) {
		try {
			if (arguments.has("_malformed")) throw new IllegalArgumentException("Tool arguments were not valid JSON");
			requireOnly(arguments, "query");
			String query = requiredString(arguments, "query").trim().toLowerCase(Locale.ROOT);
			if (query.isEmpty() || query.length() > 80) {
				throw new IllegalArgumentException("query must contain between 1 and 80 characters");
			}
			String normalizedQuery = normalize(query);
			String[] queryParts = normalizedQuery.split("_+");
			record Match(Identifier id, net.minecraft.world.entity.EntityType<?> type, int score) {}
			List<Match> matches = new ArrayList<>();
			for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
				if (!id.getNamespace().equals("minecraft")) continue;
				int score = score(normalize(id.getPath()), normalizedQuery, queryParts);
				if (score > 0) matches.add(new Match(id, BuiltInRegistries.ENTITY_TYPE.getValue(id), score));
			}
			matches.sort(Comparator.comparingInt(Match::score).reversed().thenComparing(match -> match.id().toString()));
			if (matches.size() > RETURNED_REFERENCES) matches = new ArrayList<>(matches.subList(0, RETURNED_REFERENCES));
			if (matches.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error("NO_ENTITY_MATCH",
						"No registered vanilla entity matched '" + query + "'. Try a shorter creature name.");
			}
			JsonArray references = new JsonArray();
			for (Match match : matches) {
				JsonObject encoded = new JsonObject();
				encoded.addProperty("id", match.id().toString());
				encoded.addProperty("category", match.type().getCategory().getName());
				encoded.addProperty("width", match.type().getWidth());
				encoded.addProperty("height", match.type().getHeight());
				encoded.addProperty("fireImmune", match.type().fireImmune());
				encoded.addProperty("allowedInPeaceful", match.type().isAllowedInPeaceful());
				if (DefaultAttributes.hasSupplier(match.type())) {
					@SuppressWarnings("unchecked")
					var livingType = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) match.type();
					var attributes = DefaultAttributes.getSupplier(livingType);
					JsonObject values = new JsonObject();
					addAttribute(values, attributes, "maxHealth", Attributes.MAX_HEALTH);
					addAttribute(values, attributes, "movementSpeed", Attributes.MOVEMENT_SPEED);
					addAttribute(values, attributes, "flyingSpeed", Attributes.FLYING_SPEED);
					addAttribute(values, attributes, "attackDamage", Attributes.ATTACK_DAMAGE);
					addAttribute(values, attributes, "armor", Attributes.ARMOR);
					addAttribute(values, attributes, "knockbackResistance", Attributes.KNOCKBACK_RESISTANCE);
					addAttribute(values, attributes, "followRange", Attributes.FOLLOW_RANGE);
					encoded.add("attributes", values);
				}
				references.add(encoded);
			}
			JsonObject result = new JsonObject();
			result.addProperty("query", query);
			result.add("references", references);
			return ContentGenerationDraft.ToolExecution.success(result, null);
		} catch (IllegalArgumentException error) {
			return ContentGenerationDraft.ToolExecution.error("INVALID_ARGUMENT", safeMessage(error));
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("TOOL_FAILURE", safeMessage(error));
		}
	}

	static ContentGenerationDraft.ToolExecution fetchEntityVisual(JsonObject arguments) {
		try {
			if (arguments.has("_malformed")) throw new IllegalArgumentException("Tool arguments were not valid JSON");
			requireOnly(arguments, "query");
			String query = requiredString(arguments, "query").trim().toLowerCase(Locale.ROOT);
			if (query.isEmpty() || query.length() > 80) {
				throw new IllegalArgumentException("query must contain between 1 and 80 characters");
			}
			String normalizedQuery = normalize(query);
			String[] queryParts = normalizedQuery.split("_+");
			List<ScoredEntityVisual> matches = new ArrayList<>();
			for (EntityVisualReference reference : ENTITY_VISUAL_REFERENCES) {
				int referenceScore = score(normalize(reference.name()), normalizedQuery, queryParts);
				int entityScore = score(normalize(reference.sourceEntity()), normalizedQuery, queryParts);
				int profileScore = score(reference.profile().serializedName(), normalizedQuery, queryParts);
				int textureScore = score(normalize(reference.texture()), normalizedQuery, queryParts);
				int best = Math.max(Math.max(referenceScore, entityScore), Math.max(profileScore, textureScore));
				if (best > 0) matches.add(new ScoredEntityVisual(reference, best));
			}
			matches.sort(Comparator.comparingInt(ScoredEntityVisual::score).reversed()
					.thenComparing(match -> match.reference().name()));
			if (matches.size() > RETURNED_REFERENCES) matches = new ArrayList<>(matches.subList(0, RETURNED_REFERENCES));
			if (matches.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error("NO_ENTITY_VISUAL_MATCH",
						"No supported animated vanilla entity model matched '" + query
								+ "'. Try zombie, skeleton, enderman, cow, pig, spider, creeper, blaze, or cod; otherwise author cuboids.");
			}

			JsonArray references = new JsonArray();
			List<String> failures = new ArrayList<>();
			for (ScoredEntityVisual match : matches) {
				try {
					references.add(entityVisualPreview(match.reference()));
				} catch (Exception error) {
					failures.add(match.reference().name() + ": " + safeMessage(error));
				}
			}
			if (references.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error("ENTITY_VISUAL_READ_FAILED",
						"Compatible vanilla entity textures were found but could not be read: " + String.join("; ", failures));
			}
			JsonObject result = new JsonObject();
			result.addProperty("query", query);
			result.addProperty("setEntityVanillaModelCompatible", true);
			result.add("authoringInstructions", entityVisualInstructions());
			result.add("references", references);
			if (!failures.isEmpty()) {
				JsonArray warnings = new JsonArray();
				failures.forEach(warnings::add);
				result.add("warnings", warnings);
			}
			LittleChemistry.LOGGER.info("Fetched vanilla Minecraft entity visual references for '{}': {}", query,
					matches.stream().map(match -> match.reference().name()).toList());
			return ContentGenerationDraft.ToolExecution.success(result, null);
		} catch (IllegalArgumentException error) {
			return ContentGenerationDraft.ToolExecution.error("INVALID_ARGUMENT", safeMessage(error));
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("TOOL_FAILURE", safeMessage(error));
		}
	}

	private static void addAttribute(JsonObject destination,
			net.minecraft.world.entity.ai.attributes.AttributeSupplier supplier, String name,
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		if (supplier.hasAttribute(attribute)) destination.addProperty(name, supplier.getBaseValue(attribute));
	}

	private static JsonObject entityVisualPreview(EntityVisualReference reference) throws IOException {
		BufferedImage image = readMinecraftTexture(ENTITY_TEXTURE_PREFIX + reference.texture() + ".png");
		DynamicEntityVisualProfile profile = reference.profile();
		if (image.getWidth() != profile.textureWidth() || image.getHeight() != profile.textureHeight()) {
			throw new IOException("The installed texture dimensions do not match the "
					+ profile.serializedName() + " model profile");
		}
		DynamicTextureSpec encoded = encodeIndexedTexture(image);
		JsonArray palette = new JsonArray();
		encoded.palette().forEach(palette::add);
		JsonArray rows = new JsonArray();
		encoded.rows().forEach(rows::add);
		JsonObject result = new JsonObject();
		result.addProperty("name", reference.name());
		result.addProperty("sourceEntity", reference.sourceEntity());
		result.addProperty("modelProfile", profile.serializedName());
		result.addProperty("sourceTexture", "minecraft:entity/" + reference.texture());
		result.addProperty("width", image.getWidth());
		result.addProperty("height", image.getHeight());
		result.addProperty("animated", true);
		result.add("palette", palette);
		result.add("rows", rows);
		return result;
	}

	private static JsonArray entityVisualInstructions() {
		JsonArray instructions = new JsonArray();
		instructions.add("Choose one reference and pass its modelProfile, width, height, palette, and every row to set_entity_vanilla_model.");
		instructions.add("You may recolor or carefully edit the indexed sheet, but preserve its dimensions and UV islands; moving an island paints the wrong articulated body part.");
		instructions.add("The profile supplies Minecraft's baked geometry plus walk and look animation. It does not inherit the source entity's AI, attacks, drops, sounds, overlays, held items, or other gameplay.");
		instructions.add("Palette entries are RRGGBBAA and each row character 0-F indexes that palette. Keep transparent UV space transparent.");
		return instructions;
	}

	private static BufferedImage readMinecraftTexture(String relativePath) throws IOException {
		ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft")
				.orElseThrow(() -> new IOException("Minecraft's installed resources are unavailable"));
		for (Path root : minecraft.getRootPaths()) {
			Path texture = root.resolve(relativePath);
			if (!Files.isRegularFile(texture)) continue;
			try (InputStream input = Files.newInputStream(texture)) {
				BufferedImage image = ImageIO.read(input);
				if (image != null && image.getWidth() > 0 && image.getHeight() > 0) return image;
			}
		}
		throw new IOException("Minecraft texture is unavailable: " + relativePath);
	}

	private static ContentGenerationDraft.ToolExecution fetch(JsonObject arguments, boolean textures) {
		try {
			if (arguments.has("_malformed")) throw new IllegalArgumentException("Tool arguments were not valid JSON");
			requireOnly(arguments, "query", "kind");
			String query = requiredString(arguments, "query").trim().toLowerCase(Locale.ROOT);
			String kind = requiredString(arguments, "kind").trim().toLowerCase(Locale.ROOT);
			if (query.isEmpty() || query.length() > 80) {
				throw new IllegalArgumentException("query must contain between 1 and 80 characters");
			}
			if (!kind.equals("block") && !kind.equals("item") && !kind.equals("armor") && !kind.equals("either")) {
				throw new IllegalArgumentException("kind must be block, item, armor, or either");
			}

			List<ScoredTexture> matches = matchingTextures(query, kind.equals("armor") ? "item" : kind);
			if (matches.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error(
						"NO_CONTENT_MATCH",
						"No installed vanilla item/block texture matched '" + query + "'. Try a shorter material, item, or block name."
				);
			}

			JsonArray references = new JsonArray();
			List<String> failures = new ArrayList<>();
			for (ScoredTexture match : matches) {
				try {
					references.add(textures ? texturePreview(match.texture()) : contentReference(match.texture()));
				} catch (Exception error) {
					failures.add(match.texture().id() + ": " + safeMessage(error));
				}
			}
			if (references.isEmpty()) {
				return ContentGenerationDraft.ToolExecution.error(
						"CONTENT_READ_FAILED",
						"Matching vanilla content was found but could not be read: " + String.join("; ", failures)
				);
			}

			JsonObject result = new JsonObject();
			result.addProperty("query", query);
			result.addProperty("kind", kind);
			if (textures) {
				result.addProperty("textureFormat", "little_chemistry:indexed_rgba_16x16");
				result.addProperty("setTextureCompatible", true);
			}
			result.add("references", references);
			if (!failures.isEmpty()) {
				JsonArray warnings = new JsonArray();
				failures.forEach(warnings::add);
				result.add("warnings", warnings);
			}
			LittleChemistry.LOGGER.info("Fetched vanilla Minecraft {} for '{}' ({}): {}",
					textures ? "textures" : "content", query, kind, matches.stream().map(match -> match.texture().id()).toList());
			return ContentGenerationDraft.ToolExecution.success(result, null);
		} catch (IllegalArgumentException error) {
			return ContentGenerationDraft.ToolExecution.error("INVALID_ARGUMENT", safeMessage(error));
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("TOOL_FAILURE", safeMessage(error));
		}
	}

	private static JsonObject contentReference(TextureEntry texture) {
		JsonObject result = new JsonObject();
		result.addProperty("texture", texture.id());
		JsonObject gameplay = texture.kind().equals("item") ? itemGameplay(texture.name()) : blockGameplay(texture.name());
		if (gameplay != null) {
			result.addProperty("registeredContent", true);
			result.add("gameplay", gameplay);
		} else {
			result.addProperty("registeredContent", false);
		}
		return result;
	}

	private static JsonObject itemGameplay(String textureName) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", textureName);
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) return null;
		ItemStack stack = item.getDefaultInstance();
		JsonObject result = new JsonObject();
		result.addProperty("id", id.toString());
		result.addProperty("kind", "item");
		result.addProperty("implementation", item.getClass().getSimpleName());
		result.add("itemTypes", itemTypes(stack, item));
		result.addProperty("maxStack", stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
		result.addProperty("rarity", stack.getRarity().getSerializedName());
		result.addProperty("durability", stack.getOrDefault(DataComponents.MAX_DAMAGE, 0));

		Tool tool = stack.get(DataComponents.TOOL);
		if (tool != null) result.add("tool", toolInfo(tool));
		result.add("attributes", attributeInfo(stack));

		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("hunger", food.nutrition());
			encoded.addProperty("saturation", food.saturation());
			encoded.addProperty("alwaysEdible", food.canAlwaysEat());
			result.add("food", encoded);
		}
		var consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("consumeSeconds", consumable.consumeSeconds());
			encoded.addProperty("animation", consumable.animation().getSerializedName());
			JsonArray effects = new JsonArray();
			for (var consumeEffect : consumable.onConsumeEffects()) {
				if (!(consumeEffect instanceof ApplyStatusEffectsConsumeEffect applied)) continue;
				for (var effect : applied.effects()) {
					JsonObject status = new JsonObject();
					status.addProperty("effect", effect.getEffect().unwrapKey()
							.map(key -> key.identifier().toString()).orElse("unregistered"));
					status.addProperty("durationSeconds", effect.getDuration() / 20.0F);
					status.addProperty("amplifier", effect.getAmplifier());
					status.addProperty("probability", applied.probability());
					status.addProperty("ambient", effect.isAmbient());
					status.addProperty("showParticles", effect.isVisible());
					status.addProperty("showIcon", effect.showIcon());
					effects.add(status);
				}
			}
			encoded.add("statusEffects", effects);
			result.add("consumable", encoded);
		}
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("slot", equippable.slot().getSerializedName());
			encoded.addProperty("swappable", equippable.swappable());
			equippable.assetId().ifPresent(asset -> encoded.addProperty("equipmentAsset", asset.identifier().toString()));
			result.add("equippable", encoded);
		}
		Weapon weapon = stack.get(DataComponents.WEAPON);
		if (weapon != null) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("damagePerAttack", weapon.itemDamagePerAttack());
			encoded.addProperty("disableBlockingSeconds", weapon.disableBlockingForSeconds());
			result.add("weapon", encoded);
		}
		return result;
	}

	private static JsonArray itemTypes(ItemStack stack, Item item) {
		JsonArray types = new JsonArray();
		if (stack.is(ItemTags.PICKAXES)) types.add("pickaxe");
		if (stack.is(ItemTags.AXES)) types.add("axe");
		if (stack.is(ItemTags.SHOVELS)) types.add("shovel");
		if (stack.is(ItemTags.HOES)) types.add("hoe");
		if (stack.is(ItemTags.SWORDS)) types.add("sword");
		if (item instanceof BlockItem) types.add("block_item");
		if (stack.has(DataComponents.TOOL) && types.isEmpty()) types.add("tool");
		if (stack.has(DataComponents.WEAPON)) types.add("weapon");
		if (stack.has(DataComponents.FOOD)) types.add("food");
		if (stack.has(DataComponents.CONSUMABLE)) types.add("consumable");
		if (stack.has(DataComponents.EQUIPPABLE)) types.add("equippable");
		if (stack.has(DataComponents.BLOCKS_ATTACKS)) types.add("blocks_attacks");
		if (types.isEmpty()) types.add("ordinary");
		return types;
	}

	private static JsonObject toolInfo(Tool tool) {
		JsonObject result = new JsonObject();
		result.addProperty("defaultBreakingSpeed", tool.defaultMiningSpeed());
		result.addProperty("damagePerBlock", tool.damagePerBlock());
		result.addProperty("breakingPower", breakingPower(tool));
		float maximumSpeed = tool.defaultMiningSpeed();
		JsonArray rules = new JsonArray();
		for (Tool.Rule rule : tool.rules()) {
			JsonObject encoded = new JsonObject();
			rule.blocks().unwrapKey().ifPresent(tag -> encoded.addProperty("blocks", "#" + tag.location()));
			if (rule.blocks().unwrapKey().isEmpty()) encoded.addProperty("blocks", rule.blocks().size() + " direct blocks");
			if (rule.speed().isPresent()) {
				float speed = rule.speed().get();
				encoded.addProperty("breakingSpeed", speed);
				maximumSpeed = Math.max(maximumSpeed, speed);
			}
			rule.correctForDrops().ifPresent(value -> encoded.addProperty("correctForDrops", value));
			rules.add(encoded);
		}
		result.addProperty("maximumBreakingSpeed", maximumSpeed);
		result.add("rules", rules);
		return result;
	}

	private static String breakingPower(Tool tool) {
		for (Tool.Rule rule : tool.rules()) {
			var key = rule.blocks().unwrapKey();
			if (key.isEmpty() || rule.correctForDrops().orElse(true)) continue;
			String path = key.get().location().getPath();
			if (path.startsWith("incorrect_for_") && path.endsWith("_tool")) {
				return path.substring("incorrect_for_".length(), path.length() - "_tool".length())
						.replace("wooden", "wood");
			}
		}
		return "special";
	}

	private static JsonObject attributeInfo(ItemStack stack) {
		JsonObject result = new JsonObject();
		JsonArray modifiers = new JsonArray();
		double blockReach = 0.0;
		double entityReach = 0.0;
		double attackDamage = Attributes.ATTACK_DAMAGE.value().getDefaultValue();
		double attackSpeed = Attributes.ATTACK_SPEED.value().getDefaultValue();
		ItemAttributeModifiers itemModifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry entry : itemModifiers.modifiers()) {
			JsonObject encoded = new JsonObject();
			String attribute = entry.attribute().unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
			encoded.addProperty("attribute", attribute);
			encoded.addProperty("amount", entry.modifier().amount());
			encoded.addProperty("operation", entry.modifier().operation().getSerializedName());
			encoded.addProperty("slot", entry.slot().getSerializedName());
			modifiers.add(encoded);
			if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
					&& (entry.slot() == EquipmentSlotGroup.MAINHAND || entry.slot() == EquipmentSlotGroup.HAND || entry.slot() == EquipmentSlotGroup.ANY)) {
				if (entry.attribute().is(Attributes.BLOCK_INTERACTION_RANGE)) blockReach += entry.modifier().amount();
				if (entry.attribute().is(Attributes.ENTITY_INTERACTION_RANGE)) entityReach += entry.modifier().amount();
				if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) attackDamage += entry.modifier().amount();
				if (entry.attribute().is(Attributes.ATTACK_SPEED)) attackSpeed += entry.modifier().amount();
			}
		}
		result.addProperty("attackDamage", attackDamage);
		result.addProperty("attackSpeed", attackSpeed);
		result.addProperty("blockReachBonus", blockReach);
		result.addProperty("entityReachBonus", entityReach);
		result.add("modifiers", modifiers);
		return result;
	}

	private static JsonObject blockGameplay(String textureName) {
		String blockName = resolveBlockName(textureName);
		if (blockName == null) return null;
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", blockName);
		Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		if (block == null) return null;
		BlockState state = block.defaultBlockState();
		JsonObject result = new JsonObject();
		result.addProperty("id", id.toString());
		result.addProperty("kind", "block");
		result.addProperty("implementation", block.getClass().getSimpleName());
		result.addProperty("hardness", block.defaultDestroyTime());
		result.addProperty("blastResistance", block.getExplosionResistance());
		result.addProperty("friction", block.getFriction());
		result.addProperty("lightLevel", state.getLightEmission());
		result.addProperty("solid", state.isSolid());
		result.addProperty("fullCubeCollision", state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
		result.addProperty("randomTicks", state.isRandomlyTicking());
		result.addProperty("signalSource", state.isSignalSource());
		result.addProperty("analogOutput", state.hasAnalogOutputSignal());
		result.addProperty("requiresCorrectTool", state.requiresCorrectToolForDrops());
		result.addProperty("minimumBreakingPower", minimumBreakingPower(state));
		JsonArray preferredTools = new JsonArray();
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) preferredTools.add("pickaxe");
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) preferredTools.add("axe");
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) preferredTools.add("shovel");
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) preferredTools.add("hoe");
		result.add("preferredTools", preferredTools);
		JsonObject sound = new JsonObject();
		sound.addProperty("volume", state.getSoundType().getVolume());
		sound.addProperty("pitch", state.getSoundType().getPitch());
		sound.addProperty("breakSound", BuiltInRegistries.SOUND_EVENT.getKey(state.getSoundType().getBreakSound()).toString());
		result.add("sound", sound);
		return result;
	}

	private static String resolveBlockName(String textureName) {
		if (BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("minecraft", textureName))) return textureName;
		for (String suffix : BLOCK_TEXTURE_SUFFIXES) {
			if (!textureName.endsWith(suffix)) continue;
			String candidate = textureName.substring(0, textureName.length() - suffix.length());
			if (BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("minecraft", candidate))) return candidate;
		}
		return null;
	}

	private static String minimumBreakingPower(BlockState state) {
		if (!state.requiresCorrectToolForDrops()) return "none";
		if (!state.is(BlockTags.INCORRECT_FOR_WOODEN_TOOL)) return "wood";
		if (!state.is(BlockTags.INCORRECT_FOR_GOLD_TOOL)) return "gold";
		if (!state.is(BlockTags.INCORRECT_FOR_STONE_TOOL)) return "stone";
		if (!state.is(BlockTags.INCORRECT_FOR_COPPER_TOOL)) return "copper";
		if (!state.is(BlockTags.INCORRECT_FOR_IRON_TOOL)) return "iron";
		if (!state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) return "diamond";
		if (!state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL)) return "netherite";
		return "unbreakable";
	}

	private static List<ScoredTexture> matchingTextures(String query, String kind) throws IOException {
		String normalizedQuery = normalize(query);
		String[] queryParts = normalizedQuery.split("_+");
		List<ScoredTexture> matches = new ArrayList<>();
		for (TextureEntry texture : textureIndex()) {
			if (!kind.equals("either") && !texture.kind().equals(kind)) continue;
			String normalizedName = normalize(texture.name());
			int score = score(normalizedName, normalizedQuery, queryParts);
			if (score > 0) matches.add(new ScoredTexture(texture, score));
		}
		matches.sort(Comparator.comparingInt(ScoredTexture::score).reversed().thenComparing(match -> match.texture().id()));
		return matches.size() <= RETURNED_REFERENCES ? List.copyOf(matches) : List.copyOf(matches.subList(0, RETURNED_REFERENCES));
	}

	private static List<ScoredArmorDisplayTexture> matchingArmorDisplayTextures(String query, String layer) throws IOException {
		String normalizedQuery = normalize(query);
		String[] queryParts = normalizedQuery.split("_+");
		List<ScoredArmorDisplayTexture> matches = new ArrayList<>();
		for (ArmorDisplayTextureEntry texture : armorDisplayTextureIndex()) {
			if (!texture.layer().equals(layer)) continue;
			int score = score(normalize(texture.name()), normalizedQuery, queryParts);
			if (score > 0) matches.add(new ScoredArmorDisplayTexture(texture, score));
		}
		matches.sort(Comparator.comparingInt(ScoredArmorDisplayTexture::score).reversed()
				.thenComparing(match -> match.texture().id()));
		return matches.size() <= RETURNED_REFERENCES
				? List.copyOf(matches) : List.copyOf(matches.subList(0, RETURNED_REFERENCES));
	}

	private static int score(String name, String query, String[] queryParts) {
		if (name.equals(query)) return 10_000;
		int score = 0;
		if (name.startsWith(query)) score += 4_000;
		if (name.contains(query)) score += 2_000;
		for (String part : queryParts) {
			if (part.length() < 2) continue;
			if (name.equals(part)) score += 1_000;
			else if (name.startsWith(part)) score += 500;
			else if (name.contains(part)) score += 250;
		}
		return score;
	}

	private static List<TextureEntry> textureIndex() throws IOException {
		List<TextureEntry> existing = textureIndex;
		if (existing != null) return existing;
		synchronized (MinecraftContentFetcher.class) {
			if (textureIndex != null) return textureIndex;
			ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft")
					.orElseThrow(() -> new IOException("Minecraft's installed resources are unavailable"));
			List<TextureEntry> discovered = new ArrayList<>();
			for (Path root : minecraft.getRootPaths()) {
				indexDirectory(root, BLOCK_PREFIX, "block", discovered);
				indexDirectory(root, ITEM_PREFIX, "item", discovered);
			}
			discovered.sort(Comparator.comparing(TextureEntry::id));
			if (discovered.isEmpty()) {
				throw new IOException("This Minecraft installation does not expose client block/item textures to the server process");
			}
			textureIndex = List.copyOf(discovered);
			return textureIndex;
		}
	}

	private static List<ArmorDisplayTextureEntry> armorDisplayTextureIndex() throws IOException {
		List<ArmorDisplayTextureEntry> existing = armorDisplayTextureIndex;
		if (existing != null) return existing;
		synchronized (MinecraftContentFetcher.class) {
			if (armorDisplayTextureIndex != null) return armorDisplayTextureIndex;
			ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft")
					.orElseThrow(() -> new IOException("Minecraft's installed resources are unavailable"));
			List<ArmorDisplayTextureEntry> discovered = new ArrayList<>();
			for (Path root : minecraft.getRootPaths()) {
				indexArmorDisplayDirectory(root, "humanoid", discovered);
				indexArmorDisplayDirectory(root, "humanoid_leggings", discovered);
			}
			discovered.sort(Comparator.comparing(ArmorDisplayTextureEntry::id));
			if (discovered.isEmpty()) {
				throw new IOException("This Minecraft installation does not expose humanoid equipment textures to the server process");
			}
			armorDisplayTextureIndex = List.copyOf(discovered);
			return armorDisplayTextureIndex;
		}
	}

	private static void indexDirectory(Path root, String prefix, String kind, List<TextureEntry> destination) throws IOException {
		Path directory = root.resolve(prefix);
		if (!Files.isDirectory(directory)) return;
		try (var paths = Files.walk(directory)) {
			paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".png")).forEach(path -> {
				String relative = directory.relativize(path).toString().replace('\\', '/');
				String name = relative.substring(0, relative.length() - 4);
				destination.add(new TextureEntry(kind + "/" + name, kind, name, path));
			});
		}
	}

	private static void indexArmorDisplayDirectory(Path root, String layer,
			List<ArmorDisplayTextureEntry> destination) throws IOException {
		Path directory = root.resolve(ARMOR_DISPLAY_PREFIX).resolve(layer);
		if (!Files.isDirectory(directory)) return;
		try (var paths = Files.walk(directory)) {
			paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".png")).forEach(path -> {
				String relative = directory.relativize(path).toString().replace('\\', '/');
				String name = relative.substring(0, relative.length() - 4);
				destination.add(new ArmorDisplayTextureEntry(
						"entity/equipment/" + layer + "/" + name, layer, name, path));
			});
		}
	}

	private static JsonObject armorDisplayTexturePreview(ArmorDisplayTextureEntry texture) throws IOException {
		BufferedImage image;
		try (InputStream input = Files.newInputStream(texture.path())) {
			image = ImageIO.read(input);
		}
		if (image == null || image.getWidth() != DynamicArmorDisplayTextureSpec.WIDTH
				|| image.getHeight() != DynamicArmorDisplayTextureSpec.HEIGHT) {
			throw new IOException("Vanilla humanoid equipment texture is not 64x32 pixels");
		}
		DynamicArmorDisplayTextureSpec encoded = encodeArmorDisplayTexture(image);
		JsonArray palette = new JsonArray();
		encoded.palette().forEach(palette::add);
		JsonArray rows = new JsonArray();
		encoded.rows().forEach(rows::add);
		JsonObject result = new JsonObject();
		result.addProperty("texture", texture.id());
		result.addProperty("layer", texture.layer());
		result.addProperty("sourceWidth", image.getWidth());
		result.addProperty("sourceHeight", image.getHeight());
		result.add("palette", palette);
		result.add("rows", rows);
		return result;
	}

	private static DynamicArmorDisplayTextureSpec encodeArmorDisplayTexture(BufferedImage image) {
		int[] pixels = new int[DynamicArmorDisplayTextureSpec.WIDTH * DynamicArmorDisplayTextureSpec.HEIGHT];
		Map<Integer, Integer> counts = new HashMap<>();
		for (int y = 0; y < DynamicArmorDisplayTextureSpec.HEIGHT; y++) {
			for (int x = 0; x < DynamicArmorDisplayTextureSpec.WIDTH; x++) {
				int argb = normalizeAlpha(image.getRGB(x, y));
				pixels[y * DynamicArmorDisplayTextureSpec.WIDTH + x] = argb;
				counts.merge(argb, 1, Integer::sum);
			}
		}
		List<Integer> palette = counts.entrySet().stream()
				.sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
						.thenComparing(entry -> Integer.toUnsignedLong(entry.getKey())))
				.map(Map.Entry::getKey).limit(16).toList();
		List<String> encodedPalette = palette.stream().map(MinecraftContentFetcher::rgba).toList();
		String keys = "0123456789ABCDEF";
		List<String> rows = new ArrayList<>(DynamicArmorDisplayTextureSpec.HEIGHT);
		for (int y = 0; y < DynamicArmorDisplayTextureSpec.HEIGHT; y++) {
			StringBuilder row = new StringBuilder(DynamicArmorDisplayTextureSpec.WIDTH);
			for (int x = 0; x < DynamicArmorDisplayTextureSpec.WIDTH; x++) {
				row.append(keys.charAt(nearestPaletteIndex(
						pixels[y * DynamicArmorDisplayTextureSpec.WIDTH + x], palette)));
			}
			rows.add(row.toString());
		}
		return new DynamicArmorDisplayTextureSpec(encodedPalette, rows);
	}

	private static DynamicTextureSpec encodeIndexedTexture(BufferedImage image) {
		if (image.getWidth() < 1 || image.getWidth() > 64 || image.getHeight() < 1 || image.getHeight() > 64) {
			throw new IllegalArgumentException("Entity texture dimensions must be between 1 and 64 pixels");
		}
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = new int[width * height];
		Map<Integer, Integer> counts = new HashMap<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = normalizeAlpha(image.getRGB(x, y));
				pixels[y * width + x] = argb;
				counts.merge(argb, 1, Integer::sum);
			}
		}
		List<Integer> palette = counts.entrySet().stream()
				.sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
						.thenComparing(entry -> Integer.toUnsignedLong(entry.getKey())))
				.map(Map.Entry::getKey).limit(16).toList();
		List<String> encodedPalette = palette.stream().map(MinecraftContentFetcher::rgba).toList();
		String keys = "0123456789ABCDEF";
		List<String> rows = new ArrayList<>(height);
		for (int y = 0; y < height; y++) {
			StringBuilder row = new StringBuilder(width);
			for (int x = 0; x < width; x++) {
				row.append(keys.charAt(nearestPaletteIndex(pixels[y * width + x], palette)));
			}
			rows.add(row.toString());
		}
		return new DynamicTextureSpec(encodedPalette, rows);
	}

	private static JsonArray armorDisplayTextureInstructions(String layer) {
		JsonArray instructions = new JsonArray();
		instructions.add("Choose a reference below and pass the top-level slot plus that reference's palette and all 32 rows to set_armor_display_texture; recolor or edit it while keeping every row exactly 64 palette keys long.");
		instructions.add("This is the worn 64x32 equipment UV sheet, not the 16x16 inventory icon. Do not enlarge, tile, or paste the icon into it.");
		instructions.add("Coordinates start at the top-left. Preserve transparent pixels outside UV islands so the armor model does not acquire unintended surfaces.");
		instructions.add(layer.equals("humanoid_leggings")
				? "The requested leggings use Minecraft's humanoid_leggings layer. Keep the vanilla leggings reference's waist and leg artwork in the same UV positions."
				: "The requested piece uses Minecraft's humanoid layer. Standard UV bounds are head x=0..31/y=0..15, legs x=0..15/y=16..31, torso x=16..39/y=16..31, and arms x=40..55/y=16..31; only the parts for the equipped slot are rendered.");
		instructions.add("Palette entries are RRGGBBAA. Use alpha 00 for transparent space and only alpha 00 or FF. Row characters 0-F index the palette.");
		return instructions;
	}

	private static JsonObject texturePreview(TextureEntry texture) throws IOException {
		TextureSample sample = sampleTexture(texture);
		JsonArray encodedPaletteJson = new JsonArray();
		sample.texture().palette().forEach(encodedPaletteJson::add);
		JsonArray encodedRowsJson = new JsonArray();
		sample.texture().rows().forEach(encodedRowsJson::add);
		JsonObject result = new JsonObject();
		result.addProperty("texture", texture.id());
		result.addProperty("sourceWidth", sample.sourceWidth());
		result.addProperty("sourceHeight", sample.sourceHeight());
		result.addProperty("sampledRegion", "first square frame, normalized to 16x16");
		result.add("palette", encodedPaletteJson);
		result.add("rows", encodedRowsJson);
		return result;
	}

	private static TextureSample sampleTexture(TextureEntry texture) throws IOException {
		BufferedImage image;
		try (InputStream input = Files.newInputStream(texture.path())) {
			image = ImageIO.read(input);
		}
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("Unsupported or empty PNG");

		int frameSize = Math.min(image.getWidth(), image.getHeight());
		int[] pixels = new int[256];
		Map<Integer, Integer> counts = new HashMap<>();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int sourceX = Math.min(image.getWidth() - 1, x * frameSize / 16);
				int sourceY = Math.min(image.getHeight() - 1, y * frameSize / 16);
				int argb = normalizeAlpha(image.getRGB(sourceX, sourceY));
				pixels[y * 16 + x] = argb;
				counts.merge(argb, 1, Integer::sum);
			}
		}
		List<Integer> palette = counts.entrySet().stream()
				.sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed().thenComparing(entry -> Integer.toUnsignedLong(entry.getKey())))
				.map(Map.Entry::getKey).limit(16).toList();
		List<String> encodedPalette = palette.stream().map(MinecraftContentFetcher::rgba).toList();
		String keys = "0123456789ABCDEF";
		List<String> rows = new ArrayList<>(16);
		for (int y = 0; y < 16; y++) {
			StringBuilder row = new StringBuilder(16);
			for (int x = 0; x < 16; x++) row.append(keys.charAt(nearestPaletteIndex(pixels[y * 16 + x], palette)));
			rows.add(row.toString());
		}
		return new TextureSample(new DynamicTextureSpec(encodedPalette, rows), image.getWidth(), image.getHeight());
	}

	private static int normalizeAlpha(int argb) {
		return ((argb >>> 24) & 0xFF) < 16 ? 0 : argb;
	}

	private static int nearestPaletteIndex(int argb, List<Integer> palette) {
		int exact = palette.indexOf(argb);
		if (exact >= 0) return exact;
		int bestIndex = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int index = 0; index < palette.size(); index++) {
			int candidate = palette.get(index);
			int alphaDelta = ((argb >>> 24) & 0xFF) - ((candidate >>> 24) & 0xFF);
			int redDelta = ((argb >>> 16) & 0xFF) - ((candidate >>> 16) & 0xFF);
			int greenDelta = ((argb >>> 8) & 0xFF) - ((candidate >>> 8) & 0xFF);
			int blueDelta = (argb & 0xFF) - (candidate & 0xFF);
			long distance = 2L * alphaDelta * alphaDelta + (long) redDelta * redDelta
					+ (long) greenDelta * greenDelta + (long) blueDelta * blueDelta;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private static String rgba(int argb) {
		return String.format(Locale.ROOT, "%02X%02X%02X%02X", (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF,
				argb & 0xFF, (argb >>> 24) & 0xFF);
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private static EntityVisualReference visual(String name, String sourceEntity,
			DynamicEntityVisualProfile profile, String texture) {
		return new EntityVisualReference(name, sourceEntity, profile, texture);
	}

	private static void requireOnly(JsonObject object, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String key : object.keySet()) if (!names.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing string: " + key);
		return object.get(key).getAsString();
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 500 ? safe : safe.substring(0, 500) + "…";
	}

	private record TextureEntry(String id, String kind, String name, Path path) {
	}

	private record ArmorDisplayTextureEntry(String id, String layer, String name, Path path) {
	}

	private record TextureSample(DynamicTextureSpec texture, int sourceWidth, int sourceHeight) {
	}

	private record ScoredTexture(TextureEntry texture, int score) {
	}

	private record ScoredArmorDisplayTexture(ArmorDisplayTextureEntry texture, int score) {
	}

	private record EntityVisualReference(String name, String sourceEntity,
			DynamicEntityVisualProfile profile, String texture) {
	}

	private record ScoredEntityVisual(EntityVisualReference reference, int score) {
	}
}
