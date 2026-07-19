package com.yeyito.littlechemistry.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DynamicContentJson {
	public static final int CURRENT_FORMAT = 15;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private DynamicContentJson() {
	}

	public static byte[] encode(UUID serverId, long revision, List<DynamicContentDefinition> definitions) {
		JsonObject root = new JsonObject();
		root.addProperty("format", CURRENT_FORMAT);
		root.addProperty("serverId", serverId.toString());
		root.addProperty("revision", revision);
		JsonArray entries = new JsonArray();
		definitions.stream().map(DynamicContentJson::encodeDefinition).forEach(entries::add);
		root.add("definitions", entries);
		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	/** Returns the complete canonical persisted representation of one generated definition. */
	public static JsonObject encodeDefinition(DynamicContentDefinition definition) {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", definition.type().serializedName());
		entry.addProperty("name", definition.name());
		entry.addProperty("displayName", definition.displayName());
		entry.addProperty("description", definition.description());
		entry.addProperty("rarity", definition.rarityTier().serializedName());
		entry.addProperty("textureSeed", definition.textureSeed());
		entry.addProperty("textureHash", definition.textureHash());
		if (definition.texture() != null) entry.add("texture", encodeTexture(definition.texture()));
		if (definition.armorDisplayTexture() != null) {
			entry.addProperty("armorDisplayTextureHash", definition.armorDisplayTextureHash());
			entry.add("armorDisplayTexture", encodeArmorDisplayTexture(definition.armorDisplayTexture()));
		}
		if (definition.blockModel() != null) entry.add("blockModel", encodeBlockModel(definition.blockModel()));
		switch (definition.type()) {
			case BLOCK -> entry.add("block", encodeBlock(definition.block()));
			case ITEM -> entry.add("item", encodeItem(definition.item()));
			case ARMOR -> entry.add("armor", encodeArmor(definition.armor()));
		}
		entry.addProperty("behaviorSource", definition.behaviorSource());
		return entry;
	}

	public static Decoded decode(byte[] bytes) {
		JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		int format = root.get("format").getAsInt();
		if (format < 1 || format > CURRENT_FORMAT) {
			throw new IllegalArgumentException("Unsupported dynamic content format");
		}
		UUID serverId = UUID.fromString(root.get("serverId").getAsString());
		long revision = root.get("revision").getAsLong();
		JsonArray encodedDefinitions = root.getAsJsonArray("definitions");
		if (encodedDefinitions.size() > 100_000) {
			throw new IllegalArgumentException("Too many synchronized dynamic content definitions");
		}
		List<DynamicContentDefinition> definitions = new ArrayList<>();
		for (JsonElement element : encodedDefinitions) {
			JsonObject entry = element.getAsJsonObject();
			DynamicContentType type = DynamicContentType.fromSerializedName(entry.get("type").getAsString());
			String name = entry.get("name").getAsString();
			if (!name.matches("[a-z0-9_]{1,64}")) {
				throw new IllegalArgumentException("Invalid dynamic content name in synchronized data");
			}
			String displayName = entry.get("displayName").getAsString();
			if (displayName.isBlank() || displayName.length() > 64
					|| displayName.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("Invalid dynamic content display name in synchronized data");
			}
			String description = format >= 11 && entry.has("description")
					? entry.get("description").getAsString() : "";
			long textureSeed = entry.get("textureSeed").getAsLong();
			String textureHash;
			if (format >= 2) {
				textureHash = entry.get("textureHash").getAsString();
			} else {
				try {
					textureHash = DynamicTextureAsset.sha256(DynamicTextureAsset.generate(textureSeed));
				} catch (java.io.IOException error) {
					throw new IllegalArgumentException("Could not migrate a legacy dynamic texture", error);
				}
			}
			DynamicTextureSpec texture = format >= 3 && entry.has("texture")
					? decodeTexture(entry.getAsJsonObject("texture")) : null;
			String armorDisplayTextureHash = format >= 7 && entry.has("armorDisplayTextureHash")
					? entry.get("armorDisplayTextureHash").getAsString() : null;
			DynamicArmorDisplayTextureSpec armorDisplayTexture = format >= 7 && entry.has("armorDisplayTexture")
					? decodeArmorDisplayTexture(entry.getAsJsonObject("armorDisplayTexture")) : null;
			DynamicBlockProperties block = type == DynamicContentType.BLOCK
					? format >= 3 && entry.has("block") ? decodeBlock(entry.getAsJsonObject("block")) : DynamicBlockProperties.DEFAULT
					: null;
			DynamicItemProperties item = type == DynamicContentType.ITEM
					? format >= 3 && entry.has("item") ? decodeItem(entry.getAsJsonObject("item")) : DynamicItemProperties.DEFAULT
					: null;
			DynamicArmorProperties armor = type == DynamicContentType.ARMOR
					? format >= 6 && entry.has("armor") ? decodeArmor(entry.getAsJsonObject("armor")) : null
					: null;
			DynamicRarity rarityTier = format >= 12 && entry.has("rarity")
					? DynamicRarity.parse(entry.get("rarity").getAsString())
					: DynamicRarity.fromProperties(block, item, armor);
			DynamicBlockModel blockModel = type == DynamicContentType.BLOCK && format >= 10 && entry.has("blockModel")
					? decodeBlockModel(entry.getAsJsonObject("blockModel")) : null;
			String behaviorSource;
			if (format >= 9) {
				if (!entry.has("behaviorSource")) {
					throw new IllegalArgumentException("Dynamic content is missing required Java behavior source");
				}
				behaviorSource = entry.get("behaviorSource").getAsString();
			} else if (format == 8) {
				if (!entry.has("behaviorSource")) {
					throw new IllegalArgumentException("Dynamic content is missing required Java behavior source");
				}
				behaviorSource = DynamicBehaviorSource.migrateMonolithicSource(
						entry.get("behaviorSource").getAsString());
			} else {
				String legacySource = format >= 4 && entry.has("behaviorSource")
						? entry.get("behaviorSource").getAsString() : null;
				behaviorSource = DynamicBehaviorSource.completeLegacySource(legacySource);
			}
			definitions.add(new DynamicContentDefinition(
					type, name, displayName, description, rarityTier, textureSeed, textureHash, texture,
					armorDisplayTextureHash, armorDisplayTexture, block, item, armor, behaviorSource, blockModel
			));
		}
		validateUniqueNames(definitions);
		DynamicBlockDrops.validateCatalog(definitions);
		return new Decoded(format, serverId, revision, List.copyOf(definitions));
	}

	private static JsonObject encodeTexture(DynamicTextureSpec texture) {
		JsonObject encoded = new JsonObject();
		JsonArray palette = new JsonArray();
		texture.palette().forEach(palette::add);
		encoded.add("palette", palette);
		JsonArray rows = new JsonArray();
		texture.rows().forEach(rows::add);
		encoded.add("rows", rows);
		return encoded;
	}

	private static DynamicTextureSpec decodeTexture(JsonObject encoded) {
		List<String> palette = new ArrayList<>();
		encoded.getAsJsonArray("palette").forEach(value -> palette.add(value.getAsString()));
		List<String> rows = new ArrayList<>();
		encoded.getAsJsonArray("rows").forEach(value -> rows.add(value.getAsString()));
		return new DynamicTextureSpec(palette, rows);
	}

	private static JsonObject encodeBlockModel(DynamicBlockModel model) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("particleTexture", model.particleTexture());
		JsonArray textures = new JsonArray();
		for (DynamicBlockTexture texture : model.textures()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", texture.id());
			value.addProperty("hash", texture.hash());
			value.add("texture", encodeTexture(texture.texture()));
			textures.add(value);
		}
		encoded.add("textures", textures);
		encoded.add("faces", encodeBlockFaces(model.faces()));
		JsonArray elements = new JsonArray();
		for (DynamicBlockModelElement element : model.elements()) {
			JsonObject value = new JsonObject();
			value.add("from", coordinates(element.fromX(), element.fromY(), element.fromZ()));
			value.add("to", coordinates(element.toX(), element.toY(), element.toZ()));
			value.addProperty("collision", element.collision());
			value.add("faces", encodeBlockFaces(element.faces()));
			elements.add(value);
		}
		encoded.add("elements", elements);
		return encoded;
	}

	private static DynamicBlockModel decodeBlockModel(JsonObject encoded) {
		List<DynamicBlockTexture> textures = new ArrayList<>();
		for (JsonElement element : encoded.getAsJsonArray("textures")) {
			JsonObject value = element.getAsJsonObject();
			textures.add(new DynamicBlockTexture(value.get("id").getAsString(), value.get("hash").getAsString(),
					decodeTexture(value.getAsJsonObject("texture"))));
		}
		List<DynamicBlockModelElement> elements = new ArrayList<>();
		for (JsonElement element : encoded.getAsJsonArray("elements")) {
			JsonObject value = element.getAsJsonObject();
			float[] from = decodeCoordinates(value.getAsJsonArray("from"));
			float[] to = decodeCoordinates(value.getAsJsonArray("to"));
			elements.add(new DynamicBlockModelElement(from[0], from[1], from[2], to[0], to[1], to[2],
					value.get("collision").getAsBoolean(), decodeBlockFaces(value.getAsJsonObject("faces"))));
		}
		return new DynamicBlockModel(textures, encoded.get("particleTexture").getAsString(),
				decodeBlockFaces(encoded.getAsJsonObject("faces")), elements);
	}

	private static JsonObject encodeBlockFaces(java.util.Map<net.minecraft.core.Direction, DynamicBlockModelFace> faces) {
		JsonObject encoded = new JsonObject();
		for (var entry : faces.entrySet()) {
			JsonObject face = new JsonObject();
			face.addProperty("texture", entry.getValue().texture());
			if (entry.getValue().uv() != null) {
				DynamicBlockUv uv = entry.getValue().uv();
				JsonArray coordinates = new JsonArray();
				coordinates.add(uv.u0()); coordinates.add(uv.v0()); coordinates.add(uv.u1()); coordinates.add(uv.v1());
				face.add("uv", coordinates);
			}
			encoded.add(entry.getKey().getSerializedName(), face);
		}
		return encoded;
	}

	private static java.util.Map<net.minecraft.core.Direction, DynamicBlockModelFace> decodeBlockFaces(JsonObject encoded) {
		java.util.EnumMap<net.minecraft.core.Direction, DynamicBlockModelFace> faces =
				new java.util.EnumMap<>(net.minecraft.core.Direction.class);
		for (var entry : encoded.entrySet()) {
			net.minecraft.core.Direction direction = net.minecraft.core.Direction.byName(entry.getKey());
			if (direction == null) throw new IllegalArgumentException("Unknown block model face: " + entry.getKey());
			JsonObject face = entry.getValue().getAsJsonObject();
			DynamicBlockUv uv = null;
			if (face.has("uv")) {
				float[] values = decodeCoordinates(face.getAsJsonArray("uv"), 4);
				uv = new DynamicBlockUv(values[0], values[1], values[2], values[3]);
			}
			faces.put(direction, new DynamicBlockModelFace(face.get("texture").getAsString(), uv));
		}
		return faces;
	}

	private static JsonArray coordinates(float... values) {
		JsonArray encoded = new JsonArray();
		for (float value : values) encoded.add(value);
		return encoded;
	}

	private static float[] decodeCoordinates(JsonArray encoded) {
		return decodeCoordinates(encoded, 3);
	}

	private static float[] decodeCoordinates(JsonArray encoded, int expected) {
		if (encoded.size() != expected) throw new IllegalArgumentException("Invalid block model coordinate count");
		float[] values = new float[expected];
		for (int index = 0; index < expected; index++) values[index] = encoded.get(index).getAsFloat();
		return values;
	}

	private static JsonObject encodeArmorDisplayTexture(DynamicArmorDisplayTextureSpec texture) {
		JsonObject encoded = new JsonObject();
		JsonArray palette = new JsonArray();
		texture.palette().forEach(palette::add);
		encoded.add("palette", palette);
		JsonArray rows = new JsonArray();
		texture.rows().forEach(rows::add);
		encoded.add("rows", rows);
		return encoded;
	}

	private static DynamicArmorDisplayTextureSpec decodeArmorDisplayTexture(JsonObject encoded) {
		List<String> palette = new ArrayList<>();
		encoded.getAsJsonArray("palette").forEach(value -> palette.add(value.getAsString()));
		List<String> rows = new ArrayList<>();
		encoded.getAsJsonArray("rows").forEach(value -> rows.add(value.getAsString()));
		return new DynamicArmorDisplayTextureSpec(palette, rows);
	}

	private static JsonObject encodeBlock(DynamicBlockProperties block) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("material", block.material().getSerializedName());
		encoded.addProperty("hardness", block.hardness());
		encoded.addProperty("preferredTool", block.preferredTool().serializedName());
		encoded.addProperty("requiresCorrectTool", block.requiresCorrectTool());
		encoded.addProperty("shape", block.shape().serializedName());
		encoded.addProperty("directional", block.directional());
		encoded.addProperty("rarity", block.rarity().getSerializedName());
		encoded.addProperty("redstonePower", block.redstonePower());
		encoded.addProperty("comparatorPower", block.comparatorPower());
		encoded.addProperty("lightLevel", block.lightLevel());
		encoded.addProperty("visuallyEmissive", block.visuallyEmissive());
		encoded.add("drops", encodeBlockDrops(block.drops()));
		JsonArray particles = new JsonArray();
		for (DynamicParticleEmitter emitter : block.particles()) {
			JsonObject particle = new JsonObject();
			particle.addProperty("type", emitter.type().serializedName());
			particle.addProperty("chancePerTick", emitter.chancePerTick());
			particle.addProperty("count", emitter.count());
			particle.addProperty("velocity", emitter.velocity());
			particle.addProperty("region", emitter.topSurface() ? "top" : "all");
			particles.add(particle);
		}
		encoded.add("particles", particles);
		return encoded;
	}

	private static JsonObject encodeBlockDrops(DynamicBlockDrops drops) {
		JsonObject encoded = new JsonObject();
		JsonArray entries = new JsonArray();
		for (DynamicDropEntry entry : drops.entries()) {
			JsonObject value = new JsonObject();
			value.addProperty("targetKind", entry.targetKind().serializedName());
			value.addProperty("target", entry.target());
			value.addProperty("minCount", entry.minCount());
			value.addProperty("maxCount", entry.maxCount());
			value.addProperty("chance", entry.chance());
			value.addProperty("fortune", entry.fortune().serializedName());
			entries.add(value);
		}
		encoded.add("entries", entries);
		encoded.addProperty("silkTouchDropsSelf", drops.silkTouchDropsSelf());
		encoded.addProperty("explosionDecay", drops.explosionDecay());
		return encoded;
	}

	private static DynamicBlockDrops decodeBlockDrops(JsonObject encoded) {
		JsonArray encodedEntries = encoded.getAsJsonArray("entries");
		if (encodedEntries == null) throw new IllegalArgumentException("Dynamic block drops are missing entries");
		List<DynamicDropEntry> entries = new ArrayList<>();
		for (JsonElement element : encodedEntries) {
			JsonObject value = element.getAsJsonObject();
			entries.add(new DynamicDropEntry(
					DynamicDropTargetKind.parse(value.get("targetKind").getAsString()),
					value.get("target").getAsString(),
					value.get("minCount").getAsInt(),
					value.get("maxCount").getAsInt(),
					value.get("chance").getAsDouble(),
					DynamicFortuneMode.parse(value.get("fortune").getAsString())
			));
		}
		return new DynamicBlockDrops(
				entries,
				encoded.has("silkTouchDropsSelf") && encoded.get("silkTouchDropsSelf").getAsBoolean(),
				encoded.has("explosionDecay") && encoded.get("explosionDecay").getAsBoolean()
		);
	}

	private static DynamicBlockProperties decodeBlock(JsonObject encoded) {
		List<DynamicParticleEmitter> particles = new ArrayList<>();
		JsonArray encodedParticles = encoded.getAsJsonArray("particles");
		if (encodedParticles != null) {
			for (JsonElement element : encodedParticles) {
				JsonObject particle = element.getAsJsonObject();
				particles.add(new DynamicParticleEmitter(
						DynamicParticleType.parse(particle.get("type").getAsString()),
						particle.get("chancePerTick").getAsDouble(),
						particle.get("count").getAsInt(),
						particle.get("velocity").getAsDouble(),
						"top".equals(particle.get("region").getAsString())
				));
			}
		}
		return new DynamicBlockProperties(
				DynamicMaterial.parse(encoded.get("material").getAsString()),
				encoded.get("hardness").getAsFloat(),
				DynamicTool.parse(encoded.get("preferredTool").getAsString()),
				encoded.get("requiresCorrectTool").getAsBoolean(),
				encoded.has("shape") ? DynamicBlockShape.parse(encoded.get("shape").getAsString()) : DynamicBlockShape.FULL_CUBE,
				encoded.has("directional") && encoded.get("directional").getAsBoolean(),
				encoded.has("rarity")
						? Rarity.valueOf(encoded.get("rarity").getAsString().toUpperCase(Locale.ROOT))
						: Rarity.COMMON,
				encoded.has("redstonePower") ? encoded.get("redstonePower").getAsInt() : 0,
				encoded.has("comparatorPower") ? encoded.get("comparatorPower").getAsInt() : 0,
				encoded.get("lightLevel").getAsInt(),
				encoded.get("visuallyEmissive").getAsBoolean(),
				particles,
				encoded.get("drops") instanceof JsonObject drops ? decodeBlockDrops(drops) : DynamicBlockDrops.DEFAULT
		);
	}

	private static JsonObject encodeItem(DynamicItemProperties item) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("itemType", item.itemType().serializedName());
		encoded.addProperty("heldType", item.heldType().serializedName());
		encoded.addProperty("maxStack", item.maxStack());
		encoded.addProperty("rarity", item.rarity().getSerializedName());
		encoded.addProperty("foil", item.foil());
		encoded.addProperty("enchantability", item.enchantability());
		encoded.addProperty("tool", item.tool().serializedName());
		encoded.addProperty("breakingPower", item.breakingPower().serializedName());
		encoded.addProperty("breakingSpeed", item.breakingSpeed());
		encoded.addProperty("reach", item.reach());
		encoded.addProperty("craftingUse", item.craftingUse().serializedName());
		if (item.itemType() == DynamicItemType.TOOL) {
			encoded.addProperty("attackDamage", item.attackDamage());
			encoded.addProperty("attackSpeed", item.attackSpeed());
			encoded.addProperty("durability", item.durability());
			encoded.addProperty("damagePerBlock", item.damagePerBlock());
			encoded.addProperty("damagePerAttack", item.damagePerAttack());
		}
		if (item.food() != null) {
			JsonObject food = new JsonObject();
			food.addProperty("hunger", item.food().hunger());
			food.addProperty("saturation", item.food().saturation());
			food.addProperty("consumeSeconds", item.food().consumeSeconds());
			food.addProperty("alwaysEdible", item.food().alwaysEdible());
			JsonArray effects = new JsonArray();
			for (DynamicFoodEffect effect : item.food().effects()) {
				JsonObject encodedEffect = new JsonObject();
				encodedEffect.addProperty("effect", effect.effect().toString());
				encodedEffect.addProperty("durationSeconds", effect.durationSeconds());
				encodedEffect.addProperty("amplifier", effect.amplifier());
				encodedEffect.addProperty("probability", effect.probability());
				encodedEffect.addProperty("ambient", effect.ambient());
				encodedEffect.addProperty("showParticles", effect.showParticles());
				encodedEffect.addProperty("showIcon", effect.showIcon());
				effects.add(encodedEffect);
			}
			food.add("effects", effects);
			encoded.add("food", food);
		}
		if (item.placement() != null) {
			JsonObject placement = new JsonObject();
			placement.addProperty("shape", item.placement().shape().serializedName());
			JsonArray supports = new JsonArray();
			item.placement().supports().forEach(supports::add);
			placement.add("supports", supports);
			placement.addProperty("lightLevel", item.placement().lightLevel());
			placement.addProperty("visuallyEmissive", item.placement().visuallyEmissive());
			encoded.add("placement", placement);
		}
		return encoded;
	}

	private static DynamicItemProperties decodeItem(JsonObject encoded) {
		DynamicItemProperties defaults = DynamicItemProperties.DEFAULT;
		int maxStack = encoded.get("maxStack").getAsInt();
		Rarity rarity = Rarity.valueOf(encoded.get("rarity").getAsString().toUpperCase(Locale.ROOT));
		boolean foil = encoded.get("foil").getAsBoolean();
		int enchantability = encoded.has("enchantability") ? encoded.get("enchantability").getAsInt() : 0;
		double reach = encoded.has("reach") ? encoded.get("reach").getAsDouble() : defaults.reach();
		DynamicTool tool = encoded.has("tool") ? DynamicTool.parse(encoded.get("tool").getAsString()) : defaults.tool();
		DynamicItemType itemType = encoded.has("itemType")
				? DynamicItemType.parse(encoded.get("itemType").getAsString())
				: tool == DynamicTool.NONE ? DynamicItemType.ITEM : DynamicItemType.TOOL;
		DynamicHeldType heldType = encoded.has("heldType")
				? DynamicHeldType.parse(encoded.get("heldType").getAsString())
				: DynamicHeldType.legacyDefaultFor(itemType);
		DynamicCraftingUse craftingUse = encoded.has("craftingUse")
				? DynamicCraftingUse.parse(encoded.get("craftingUse").getAsString())
				: DynamicCraftingUse.CONSUME;
		JsonObject encodedFood = encoded.get("food") instanceof JsonObject value ? value
				: encoded.get("consumable") instanceof JsonObject legacy ? legacy : null;
		if (itemType == DynamicItemType.ITEM && encodedFood != null) itemType = DynamicItemType.FOOD;
		if (itemType != DynamicItemType.TOOL) {
			DynamicFoodProperties food = null;
			if (encodedFood != null) {
				List<DynamicFoodEffect> effects = new ArrayList<>();
				if (encodedFood.get("effects") instanceof JsonArray encodedEffects) {
					for (JsonElement element : encodedEffects) {
						JsonObject effect = element.getAsJsonObject();
						effects.add(new DynamicFoodEffect(
								Identifier.parse(effect.get("effect").getAsString()),
								effect.get("durationSeconds").getAsFloat(), effect.get("amplifier").getAsInt(),
								effect.has("probability") ? effect.get("probability").getAsFloat() : 1.0F,
								effect.has("ambient") && effect.get("ambient").getAsBoolean(),
								!effect.has("showParticles") || effect.get("showParticles").getAsBoolean(),
								!effect.has("showIcon") || effect.get("showIcon").getAsBoolean()
						));
					}
				}
				food = new DynamicFoodProperties(
						encodedFood.has("hunger") ? encodedFood.get("hunger").getAsInt() : encodedFood.get("nutrition").getAsInt(),
						encodedFood.get("saturation").getAsFloat(), encodedFood.get("consumeSeconds").getAsFloat(),
						encodedFood.get("alwaysEdible").getAsBoolean(), effects);
			}
			DynamicPlacementProperties placement = null;
			if (itemType == DynamicItemType.ITEM && encoded.get("placement") instanceof JsonObject value) {
				List<String> supports = new ArrayList<>();
				value.getAsJsonArray("supports").forEach(support -> supports.add(support.getAsString()));
				placement = new DynamicPlacementProperties(
						DynamicPlacedShape.parse(value.get("shape").getAsString()), supports,
						value.has("lightLevel") ? value.get("lightLevel").getAsInt() : 0,
						value.has("visuallyEmissive") && value.get("visuallyEmissive").getAsBoolean());
			}
			return new DynamicItemProperties(itemType, heldType, maxStack, rarity, foil, enchantability, reach,
					DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0,
					food, placement, craftingUse);
		}
		return new DynamicItemProperties(
				DynamicItemType.TOOL, heldType, 1, rarity, foil, enchantability, reach,
				tool,
				encoded.has("breakingPower") ? DynamicBreakingPower.parse(encoded.get("breakingPower").getAsString()) : DynamicBreakingPower.WOOD,
				encoded.has("breakingSpeed") ? encoded.get("breakingSpeed").getAsFloat() : 2.0F,
				encoded.has("attackDamage") ? encoded.get("attackDamage").getAsDouble() : 1.0,
				encoded.has("attackSpeed") ? encoded.get("attackSpeed").getAsDouble() : 1.2,
				encoded.has("durability") ? encoded.get("durability").getAsInt() : 250,
				encoded.has("damagePerBlock") ? encoded.get("damagePerBlock").getAsInt() : 1,
				encoded.has("damagePerAttack") ? encoded.get("damagePerAttack").getAsInt() : 2,
				null,
				null,
				craftingUse
		);
	}

	private static JsonObject encodeArmor(DynamicArmorProperties armor) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("slot", armor.slot().serializedName());
		encoded.addProperty("rarity", armor.rarity().getSerializedName());
		encoded.addProperty("foil", armor.foil());
		encoded.addProperty("enchantability", armor.enchantability());
		encoded.addProperty("defense", armor.defense());
		encoded.addProperty("toughness", armor.toughness());
		encoded.addProperty("knockbackResistance", armor.knockbackResistance());
		encoded.addProperty("durability", armor.durability());
		return encoded;
	}

	private static DynamicArmorProperties decodeArmor(JsonObject encoded) {
		return new DynamicArmorProperties(
				DynamicArmorSlot.parse(encoded.get("slot").getAsString()),
				Rarity.valueOf(encoded.get("rarity").getAsString().toUpperCase(Locale.ROOT)),
				encoded.get("foil").getAsBoolean(),
				encoded.get("enchantability").getAsInt(),
				encoded.get("defense").getAsDouble(),
				encoded.get("toughness").getAsDouble(),
				encoded.get("knockbackResistance").getAsDouble(),
				encoded.get("durability").getAsInt()
		);
	}

	private static void validateUniqueNames(List<DynamicContentDefinition> definitions) {
		java.util.Set<String> names = new java.util.HashSet<>();
		for (DynamicContentDefinition definition : definitions) {
			if (!names.add(definition.name())) {
				throw new IllegalArgumentException("Duplicate dynamic content name");
			}
		}
	}

	public record Decoded(int format, UUID serverId, long revision, List<DynamicContentDefinition> definitions) {
	}
}
