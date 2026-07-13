package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
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
	private static final int RETURNED_REFERENCES = 6;
	private static final List<String> BLOCK_TEXTURE_SUFFIXES = List.of(
			"_front_on", "_side_on", "_top_on", "_bottom_on", "_front", "_side", "_top", "_bottom", "_back", "_on"
	);
	private static volatile List<TextureEntry> textureIndex;

	private MinecraftContentFetcher() {
	}

	static ContentGenerationDraft.ToolExecution fetch(JsonObject arguments) {
		return fetch(arguments, false);
	}

	static ContentGenerationDraft.ToolExecution fetchTexture(JsonObject arguments) {
		return fetch(arguments, true);
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
			if (!kind.equals("block") && !kind.equals("item") && !kind.equals("either")) {
				throw new IllegalArgumentException("kind must be block, item, or either");
			}

			List<ScoredTexture> matches = matchingTextures(query, kind);
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

	private record TextureSample(DynamicTextureSpec texture, int sourceWidth, int sourceHeight) {
	}

	private record ScoredTexture(TextureEntry texture, int score) {
	}
}
