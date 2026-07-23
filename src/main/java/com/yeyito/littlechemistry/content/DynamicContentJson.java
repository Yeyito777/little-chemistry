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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DynamicContentJson {
	/** Formats 20 and 21 belonged to an abandoned development schema and must be restored from a format-19 backup. */
	public static final int CURRENT_FORMAT = 22;
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
		if (definition.entityModel() != null) entry.add("entityModel", encodeEntityModel(definition.entityModel()));
		if (!definition.itemVisuals().isEmpty()) entry.add("itemVisuals", encodeItemVisuals(definition.itemVisuals()));
		entry.add("customParticles", encodeCustomParticles(definition.customParticles()));
		if (definition.workstation() != null) {
			entry.add("workstation", DynamicWorkstationJson.encode(definition.workstation()));
		}
		switch (definition.type()) {
			case BLOCK -> entry.add("block", encodeBlock(definition.block()));
			case ITEM -> entry.add("item", encodeItem(definition.item()));
			case ARMOR -> entry.add("armor", encodeArmor(definition.armor()));
			case ENTITY -> entry.add("entity", encodeEntity(definition.entity()));
		}
		entry.addProperty("behaviorSource", definition.behaviorSource());
		return entry;
	}

	public static Decoded decode(byte[] bytes) {
		JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		int format = storedFormat(root);
		if (!isSupportedFormat(format)) {
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
			DynamicEntityProperties entity = type == DynamicContentType.ENTITY && entry.has("entity")
					? decodeEntity(entry.getAsJsonObject("entity"), format) : null;
			DynamicRarity rarityTier = format >= 12 && entry.has("rarity")
					? DynamicRarity.parse(entry.get("rarity").getAsString())
					: DynamicRarity.fromProperties(block, item, armor);
			DynamicBlockModel blockModel = type == DynamicContentType.BLOCK && format >= 10 && entry.has("blockModel")
					? decodeBlockModel(entry.getAsJsonObject("blockModel")) : null;
			DynamicEntityModel entityModel = type == DynamicContentType.ENTITY && entry.has("entityModel")
					? decodeCompatibleEntityModel(entry.getAsJsonObject("entityModel")) : null;
			List<DynamicParticleDefinition> customParticles = entry.has("customParticles")
					? decodeCustomParticles(entry.getAsJsonArray("customParticles")) : List.of();
				DynamicWorkstationSpec workstation = format >= 18 && entry.has("workstation")
						? DynamicWorkstationJson.decode(entry.getAsJsonObject("workstation")) : null;
				DynamicItemVisuals itemVisuals = format >= 22 && entry.has("itemVisuals")
						? decodeItemVisuals(entry.getAsJsonArray("itemVisuals")) : DynamicItemVisuals.NONE;
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
						armorDisplayTextureHash, armorDisplayTexture, block, item, armor, behaviorSource, blockModel,
						customParticles, workstation, entity, entityModel, itemVisuals
				));
		}
		validateUniqueNames(definitions);
		DynamicBlockDrops.validateCatalog(definitions);
		return new Decoded(format, serverId, revision, List.copyOf(definitions));
	}

	static int storedFormat(byte[] bytes) {
		return storedFormat(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
	}

	static boolean isSupportedFormat(int format) {
		return format >= 1 && format <= 19 || format == CURRENT_FORMAT;
	}

	private static int storedFormat(JsonObject root) {
		if (root.get("format") == null || !root.get("format").isJsonPrimitive()
				|| !root.getAsJsonPrimitive("format").isNumber()) {
			throw new IllegalArgumentException("Dynamic content format is missing or invalid");
		}
		try {
			BigDecimal encoded = root.getAsJsonPrimitive("format").getAsBigDecimal();
			return encoded.intValueExact();
		} catch (ArithmeticException | NumberFormatException invalid) {
			throw new IllegalArgumentException("Dynamic content format must be an exact integer", invalid);
		}
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

	private static JsonArray encodeItemVisuals(DynamicItemVisuals visuals) {
		JsonArray encoded = new JsonArray();
		for (DynamicItemTexture state : visuals.states()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", state.id());
			value.addProperty("hash", state.hash());
			value.add("texture", encodeTexture(state.texture()));
			encoded.add(value);
		}
		return encoded;
	}

	private static DynamicItemVisuals decodeItemVisuals(JsonArray encoded) {
		List<DynamicItemTexture> states = new ArrayList<>();
		for (JsonElement element : encoded) {
			JsonObject value = element.getAsJsonObject();
			states.add(new DynamicItemTexture(value.get("id").getAsString(), value.get("hash").getAsString(),
					decodeTexture(value.getAsJsonObject("texture"))));
		}
		return new DynamicItemVisuals(states);
	}

	private static JsonArray encodeCustomParticles(List<DynamicParticleDefinition> particles) {
		JsonArray encoded = new JsonArray();
		for (DynamicParticleDefinition particle : particles) {
			JsonObject value = new JsonObject();
			value.addProperty("id", particle.id());
			JsonArray frames = new JsonArray();
			for (DynamicParticleFrame frame : particle.frames()) {
				JsonObject encodedFrame = new JsonObject();
				encodedFrame.addProperty("hash", frame.textureHash());
				encodedFrame.add("texture", encodeTexture(frame.texture()));
				frames.add(encodedFrame);
			}
			value.add("frames", frames);
			value.addProperty("frameTicks", particle.frameTicks());
			value.addProperty("loop", particle.loop());
			value.addProperty("lifetimeTicks", particle.lifetimeTicks());
			value.addProperty("startSize", particle.startSize());
			value.addProperty("endSize", particle.endSize());
			value.addProperty("startColor", particle.startColor());
			value.addProperty("endColor", particle.endColor());
			value.addProperty("gravity", particle.gravity());
			value.addProperty("friction", particle.friction());
			value.addProperty("collision", particle.collision());
			value.addProperty("emissive", particle.emissive());
			value.addProperty("spin", particle.spin());
			encoded.add(value);
		}
		return encoded;
	}

	private static List<DynamicParticleDefinition> decodeCustomParticles(JsonArray encoded) {
		List<DynamicParticleDefinition> particles = new ArrayList<>();
		for (JsonElement element : encoded) {
			JsonObject value = element.getAsJsonObject();
			List<DynamicParticleFrame> frames = new ArrayList<>();
			for (JsonElement frameElement : value.getAsJsonArray("frames")) {
				JsonObject frame = frameElement.getAsJsonObject();
				frames.add(new DynamicParticleFrame(
						frame.get("hash").getAsString(), decodeTexture(frame.getAsJsonObject("texture"))));
			}
			particles.add(new DynamicParticleDefinition(
					value.get("id").getAsString(),
					frames,
					value.get("frameTicks").getAsInt(),
					value.get("loop").getAsBoolean(),
					value.get("lifetimeTicks").getAsInt(),
					value.get("startSize").getAsFloat(),
					value.get("endSize").getAsFloat(),
					value.get("startColor").getAsString(),
					value.get("endColor").getAsString(),
					value.get("gravity").getAsFloat(),
					value.get("friction").getAsFloat(),
					value.get("collision").getAsBoolean(),
					value.get("emissive").getAsBoolean(),
					value.get("spin").getAsFloat()
			));
		}
		return List.copyOf(particles);
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

	private static JsonObject encodeEntityModel(DynamicEntityModel model) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("profile", model.profile().serializedName());
		if (model.usesVanillaModel()) {
			encoded.add("texture", encodeBlockTexture(model.vanillaTexture()));
		} else {
			encoded.add("geometry", encodeBlockModel(model.geometry()));
		}
		return encoded;
	}

	/** Format 14 entity branches stored the raw cuboid model; tagged profiles were introduced afterward. */
	private static DynamicEntityModel decodeCompatibleEntityModel(JsonObject encoded) {
		return encoded.has("profile")
				? decodeEntityModel(encoded)
				: new DynamicEntityModel(decodeBlockModel(encoded));
	}

	private static DynamicEntityModel decodeEntityModel(JsonObject encoded) {
		DynamicEntityVisualProfile profile = DynamicEntityVisualProfile.parse(encoded.get("profile").getAsString());
		if (profile == DynamicEntityVisualProfile.CUSTOM) {
			if (!encoded.has("geometry") || encoded.has("texture")) {
				throw new IllegalArgumentException("Custom entity model data is malformed");
			}
			return new DynamicEntityModel(decodeBlockModel(encoded.getAsJsonObject("geometry")));
		}
		if (!encoded.has("texture") || encoded.has("geometry")) {
			throw new IllegalArgumentException("Vanilla-profile entity model data is malformed");
		}
		return DynamicEntityModel.vanilla(profile, decodeBlockTexture(encoded.getAsJsonObject("texture")));
	}

	private static JsonObject encodeBlockTexture(DynamicBlockTexture texture) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("id", texture.id());
		encoded.addProperty("hash", texture.hash());
		encoded.add("texture", encodeTexture(texture.texture()));
		return encoded;
	}

	private static DynamicBlockTexture decodeBlockTexture(JsonObject encoded) {
		return new DynamicBlockTexture(encoded.get("id").getAsString(), encoded.get("hash").getAsString(),
				decodeTexture(encoded.getAsJsonObject("texture")));
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
			particle.addProperty("particle", emitter.particle());
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
						particle.has("particle") ? particle.get("particle").getAsString()
								: particle.get("type").getAsString(),
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
		encoded.addProperty("fuelBurnTicks", item.fuelBurnTicks());
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
		int fuelBurnTicks = encoded.has("fuelBurnTicks") ? encoded.get("fuelBurnTicks").getAsInt() : 0;
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
					food, placement, craftingUse, fuelBurnTicks);
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
				craftingUse,
				fuelBurnTicks
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

	private static JsonObject encodeEntity(DynamicEntityProperties entity) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("movement", entity.movement().serializedName());
		encoded.addProperty("disposition", entity.disposition().serializedName());
		encoded.addProperty("width", entity.width());
		encoded.addProperty("height", entity.height());
		encoded.addProperty("eyeHeight", entity.eyeHeight());
		encoded.addProperty("maxHealth", entity.maxHealth());
		encoded.addProperty("movementSpeed", entity.movementSpeed());
		encoded.addProperty("attackDamage", entity.attackDamage());
		encoded.addProperty("armor", entity.armor());
		encoded.addProperty("knockbackResistance", entity.knockbackResistance());
		encoded.addProperty("followRange", entity.followRange());
		encoded.addProperty("experienceReward", entity.experienceReward());
		encoded.addProperty("fireImmune", entity.fireImmune());
		encoded.addProperty("ambientSound", entity.ambientSound().toString());
		encoded.addProperty("hurtSound", entity.hurtSound().toString());
		encoded.addProperty("deathSound", entity.deathSound().toString());
		JsonArray drops = new JsonArray();
		for (DynamicEntityDrop drop : entity.drops()) {
			JsonObject value = new JsonObject();
			value.addProperty("item", drop.item().toString());
			value.addProperty("minimum", drop.minimum());
			value.addProperty("maximum", drop.maximum());
			value.addProperty("chance", drop.chance());
			drops.add(value);
		}
		encoded.add("drops", drops);
		return encoded;
	}

	private static DynamicEntityProperties decodeEntity(JsonObject encoded, int format) {
		List<DynamicEntityDrop> drops = new ArrayList<>();
		for (JsonElement element : encoded.getAsJsonArray("drops")) {
			JsonObject value = element.getAsJsonObject();
			drops.add(new DynamicEntityDrop(
					Identifier.parse(value.get("item").getAsString()),
					value.get("minimum").getAsInt(),
					value.get("maximum").getAsInt(),
					value.get("chance").getAsDouble()));
		}
		double maxHealth = encoded.get("maxHealth").getAsDouble();
		double armor = encoded.get("armor").getAsDouble();
		// The standalone entity branch briefly persisted values above Minecraft 26.2's effective
		// attribute limits in formats 14 and 15. Clamp those development-world definitions instead
		// of making the entire world catalog unloadable after the schema was corrected.
		if (format <= 15) {
			maxHealth = Math.min(maxHealth, 1024.0);
			armor = Math.min(armor, 30.0);
		}
		return new DynamicEntityProperties(
				DynamicEntityMovement.parse(encoded.get("movement").getAsString()),
				DynamicEntityDisposition.parse(encoded.get("disposition").getAsString()),
				encoded.get("width").getAsFloat(),
				encoded.get("height").getAsFloat(),
				encoded.get("eyeHeight").getAsFloat(),
				maxHealth,
				encoded.get("movementSpeed").getAsDouble(),
				encoded.get("attackDamage").getAsDouble(),
				armor,
				encoded.get("knockbackResistance").getAsDouble(),
				encoded.get("followRange").getAsDouble(),
				encoded.get("experienceReward").getAsInt(),
				encoded.get("fireImmune").getAsBoolean(),
				Identifier.parse(encoded.get("ambientSound").getAsString()),
				Identifier.parse(encoded.get("hurtSound").getAsString()),
				Identifier.parse(encoded.get("deathSound").getAsString()),
				drops);
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
