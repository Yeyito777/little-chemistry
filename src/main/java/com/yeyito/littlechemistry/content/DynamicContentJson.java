package com.yeyito.littlechemistry.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DynamicContentJson {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private DynamicContentJson() {
	}

	public static byte[] encode(UUID serverId, long revision, List<DynamicContentDefinition> definitions) {
		JsonObject root = new JsonObject();
		root.addProperty("format", 5);
		root.addProperty("serverId", serverId.toString());
		root.addProperty("revision", revision);
		JsonArray entries = new JsonArray();
		for (DynamicContentDefinition definition : definitions) {
			JsonObject entry = new JsonObject();
			entry.addProperty("type", definition.type().serializedName());
			entry.addProperty("name", definition.name());
			entry.addProperty("displayName", definition.displayName());
			entry.addProperty("textureSeed", definition.textureSeed());
			entry.addProperty("textureHash", definition.textureHash());
			if (definition.texture() != null) {
				entry.add("texture", encodeTexture(definition.texture()));
			}
			if (definition.type() == DynamicContentType.BLOCK) {
				entry.add("block", encodeBlock(definition.block()));
			} else {
				entry.add("item", encodeItem(definition.item()));
			}
			if (definition.hasBehavior()) {
				entry.addProperty("behaviorSource", definition.behaviorSource());
			}
			entries.add(entry);
		}
		root.add("definitions", entries);
		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	public static Decoded decode(byte[] bytes) {
		JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		int format = root.get("format").getAsInt();
		if (format < 1 || format > 5) {
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
			DynamicBlockProperties block = type == DynamicContentType.BLOCK
					? format >= 3 && entry.has("block") ? decodeBlock(entry.getAsJsonObject("block")) : DynamicBlockProperties.DEFAULT
					: null;
			DynamicItemProperties item = type == DynamicContentType.ITEM
					? format >= 3 && entry.has("item") ? decodeItem(entry.getAsJsonObject("item")) : DynamicItemProperties.DEFAULT
					: null;
			String behaviorSource = format >= 4 && entry.has("behaviorSource")
					? entry.get("behaviorSource").getAsString() : null;
			definitions.add(new DynamicContentDefinition(
					type, name, displayName, textureSeed, textureHash, texture, block, item, behaviorSource
			));
		}
		validateUniqueNames(definitions);
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

	private static JsonObject encodeBlock(DynamicBlockProperties block) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("material", block.material().getSerializedName());
		encoded.addProperty("hardness", block.hardness());
		encoded.addProperty("preferredTool", block.preferredTool().serializedName());
		encoded.addProperty("requiresCorrectTool", block.requiresCorrectTool());
		encoded.addProperty("shape", block.shape().serializedName());
		encoded.addProperty("redstonePower", block.redstonePower());
		encoded.addProperty("comparatorPower", block.comparatorPower());
		encoded.addProperty("lightLevel", block.lightLevel());
		encoded.addProperty("visuallyEmissive", block.visuallyEmissive());
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
				encoded.has("redstonePower") ? encoded.get("redstonePower").getAsInt() : 0,
				encoded.has("comparatorPower") ? encoded.get("comparatorPower").getAsInt() : 0,
				encoded.get("lightLevel").getAsInt(),
				encoded.get("visuallyEmissive").getAsBoolean(),
				particles
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
					DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0, food, placement);
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
				null
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
