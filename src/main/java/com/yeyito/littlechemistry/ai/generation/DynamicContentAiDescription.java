package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicArmorProperties;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicDropEntry;
import com.yeyito.littlechemistry.content.DynamicFoodEffect;
import com.yeyito.littlechemistry.content.DynamicFoodProperties;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicParticleEmitter;
import com.yeyito.littlechemistry.content.DynamicPlacementProperties;
import com.yeyito.littlechemistry.content.DynamicWorkstationJson;

/** Produces complete and summary views of generated content for the AI agents. */
public final class DynamicContentAiDescription {
	private DynamicContentAiDescription() {
	}

	public static JsonObject describe(DynamicContentDefinition definition) {
		JsonObject result = summarize(definition);
		result.getAsJsonObject("behavior").addProperty("javaSource", definition.behaviorSource());
		if (definition.workstation() != null) {
			result.add("workstation", DynamicWorkstationJson.encode(definition.workstation()));
		}
		return result;
	}

	/** Returns searchable gameplay and capability data without embedding the potentially large Java source. */
	public static JsonObject summarize(DynamicContentDefinition definition) {
		JsonObject result = new JsonObject();
		result.addProperty("contentId", LittleChemistry.MOD_ID + ":" + definition.name());
		result.addProperty("type", definition.type().serializedName());
		result.addProperty("name", definition.name());
		result.addProperty("displayName", definition.displayName());
		result.addProperty("description", definition.description());
		result.addProperty("rarity", definition.rarityTier().serializedName());
		result.add("gameplayProperties", switch (definition.type()) {
			case BLOCK -> describeBlock(definition.block());
			case ITEM -> describeItem(definition.item());
			case ARMOR -> describeArmor(definition.armor());
			case ENTITY -> describeEntity(definition.entity());
		});
		if (definition.blockModel() != null) {
			JsonObject model = new JsonObject();
			model.addProperty("textureCount", definition.blockModel().textures().size());
			model.addProperty("customElementCount", definition.blockModel().elements().size());
			model.addProperty("particleTexture", definition.blockModel().particleTexture());
			JsonArray textures = new JsonArray();
			for (var texture : definition.blockModel().textures()) {
				JsonObject encoded = new JsonObject();
				encoded.addProperty("id", texture.id());
				encoded.addProperty("width", texture.texture().width());
				encoded.addProperty("height", texture.texture().height());
				textures.add(encoded);
			}
			model.add("textures", textures);
			result.add("visualModel", model);
		}
		if (definition.entityModel() != null) {
			JsonObject model = new JsonObject();
			model.addProperty("profile", definition.entityModel().profile().serializedName());
			model.addProperty("animatedVanillaModel", definition.entityModel().usesVanillaModel());
			model.addProperty("textureCount", definition.entityModel().textures().size());
			model.addProperty("cuboidCount", definition.entityModel().elements().size());
			JsonArray textures = new JsonArray();
			for (var texture : definition.entityModel().textures()) {
				JsonObject encoded = new JsonObject();
				encoded.addProperty("id", texture.id());
				encoded.addProperty("width", texture.texture().width());
				encoded.addProperty("height", texture.texture().height());
				textures.add(encoded);
			}
			model.add("textures", textures);
			result.add("visualModel", model);
		}
		JsonArray customParticles = new JsonArray();
		for (var particle : definition.customParticles()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("id", particle.id());
			encoded.addProperty("frameCount", particle.frames().size());
			encoded.addProperty("frameWidth", particle.frames().getFirst().texture().width());
			encoded.addProperty("frameHeight", particle.frames().getFirst().texture().height());
			encoded.addProperty("frameTicks", particle.frameTicks());
			encoded.addProperty("loop", particle.loop());
			encoded.addProperty("lifetimeTicks", particle.lifetimeTicks());
			encoded.addProperty("startSize", particle.startSize());
			encoded.addProperty("endSize", particle.endSize());
			encoded.addProperty("startColor", particle.startColor());
			encoded.addProperty("endColor", particle.endColor());
			encoded.addProperty("gravity", particle.gravity());
			encoded.addProperty("friction", particle.friction());
			encoded.addProperty("collision", particle.collision());
			encoded.addProperty("emissive", particle.emissive());
			encoded.addProperty("spin", particle.spin());
			customParticles.add(encoded);
		}
		result.add("customParticles", customParticles);
		result.addProperty("isWorkstation", definition.workstation() != null);
		if (definition.workstation() != null) {
			JsonObject workstation = new JsonObject();
			workstation.addProperty("processDescription", definition.workstation().processDescription());
			workstation.addProperty("slotCount", definition.workstation().slots().size());
			JsonArray slots = new JsonArray();
			for (var slot : definition.workstation().slots()) {
				JsonObject encoded = new JsonObject();
				encoded.addProperty("id", slot.id());
				encoded.addProperty("role", slot.role().serializedName());
				slots.add(encoded);
			}
			workstation.add("slots", slots);
			workstation.addProperty("labelCount", definition.workstation().ui().labels().size());
			workstation.addProperty("gaugeCount", definition.workstation().ui().gauges().size());
			workstation.addProperty("buttonCount", definition.workstation().ui().buttons().size());
			JsonArray channels = new JsonArray();
			definition.workstation().ui().stateChannels().forEach(channel -> channels.add(channel.id()));
			workstation.add("stateChannels", channels);
			result.add("workstation", workstation);
		}
		result.add("behavior", summarizeBehavior(definition.behaviorSource()));
		return result;
	}

	private static JsonObject describeBlock(DynamicBlockProperties block) {
		JsonObject result = new JsonObject();
		result.addProperty("material", block.material().getSerializedName());
		result.addProperty("hardness", block.hardness());
		result.addProperty("preferredTool", block.preferredTool().serializedName());
		result.addProperty("requiresCorrectTool", block.requiresCorrectTool());
		result.addProperty("shape", block.shape().serializedName());
		result.addProperty("directional", block.directional());
		result.addProperty("redstonePower", block.redstonePower());
		result.addProperty("comparatorPower", block.comparatorPower());
		result.addProperty("lightLevel", block.lightLevel());
		result.addProperty("visuallyEmissive", block.visuallyEmissive());
		JsonObject drops = new JsonObject();
		JsonArray dropEntries = new JsonArray();
		for (DynamicDropEntry entry : block.drops().entries()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("targetKind", entry.targetKind().serializedName());
			encoded.addProperty("target", entry.target());
			encoded.addProperty("minCount", entry.minCount());
			encoded.addProperty("maxCount", entry.maxCount());
			encoded.addProperty("chance", entry.chance());
			encoded.addProperty("fortune", entry.fortune().serializedName());
			dropEntries.add(encoded);
		}
		drops.add("entries", dropEntries);
		drops.addProperty("silkTouchDropsSelf", block.drops().silkTouchDropsSelf());
		drops.addProperty("explosionDecay", block.drops().explosionDecay());
		result.add("drops", drops);
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
		result.add("particles", particles);
		return result;
	}

	private static JsonObject describeItem(DynamicItemProperties item) {
		JsonObject result = new JsonObject();
		result.addProperty("itemType", item.itemType().serializedName());
		result.addProperty("heldType", item.heldType().serializedName());
		result.addProperty("maxStack", item.maxStack());
		result.addProperty("craftingUse", item.craftingUse().serializedName());
		result.addProperty("fuelBurnTicks", item.fuelBurnTicks());
		result.addProperty("foil", item.foil());
		result.addProperty("enchantability", item.enchantability());
		result.addProperty("reach", item.reach());
		if (item.itemType() == DynamicItemType.TOOL) {
			JsonObject tool = new JsonObject();
			tool.addProperty("category", item.tool().serializedName());
			tool.addProperty("breakingPower", item.breakingPower().serializedName());
			tool.addProperty("breakingSpeed", item.breakingSpeed());
			tool.addProperty("attackDamage", item.attackDamage());
			tool.addProperty("attackSpeed", item.attackSpeed());
			tool.addProperty("durability", item.durability());
			tool.addProperty("damagePerBlock", item.damagePerBlock());
			tool.addProperty("damagePerAttack", item.damagePerAttack());
			result.add("tool", tool);
		}
		if (item.food() != null) result.add("food", describeFood(item.food()));
		if (item.placement() != null) result.add("placement", describePlacement(item.placement()));
		return result;
	}

	private static JsonObject describeFood(DynamicFoodProperties food) {
		JsonObject result = new JsonObject();
		result.addProperty("hunger", food.hunger());
		result.addProperty("saturation", food.saturation());
		result.addProperty("consumeSeconds", food.consumeSeconds());
		result.addProperty("alwaysEdible", food.alwaysEdible());
		JsonArray effects = new JsonArray();
		for (DynamicFoodEffect effect : food.effects()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("effect", effect.effect().toString());
			encoded.addProperty("durationSeconds", effect.durationSeconds());
			encoded.addProperty("amplifier", effect.amplifier());
			encoded.addProperty("probability", effect.probability());
			encoded.addProperty("ambient", effect.ambient());
			encoded.addProperty("showParticles", effect.showParticles());
			encoded.addProperty("showIcon", effect.showIcon());
			effects.add(encoded);
		}
		result.add("effects", effects);
		return result;
	}

	private static JsonObject describePlacement(DynamicPlacementProperties placement) {
		JsonObject result = new JsonObject();
		result.addProperty("shape", placement.shape().serializedName());
		JsonArray supports = new JsonArray();
		placement.supports().forEach(supports::add);
		result.add("supports", supports);
		result.addProperty("lightLevel", placement.lightLevel());
		result.addProperty("visuallyEmissive", placement.visuallyEmissive());
		return result;
	}

	private static JsonObject describeArmor(DynamicArmorProperties armor) {
		JsonObject result = new JsonObject();
		result.addProperty("slot", armor.slot().serializedName());
		result.addProperty("foil", armor.foil());
		result.addProperty("enchantability", armor.enchantability());
		result.addProperty("defense", armor.defense());
		result.addProperty("toughness", armor.toughness());
		result.addProperty("knockbackResistance", armor.knockbackResistance());
		result.addProperty("durability", armor.durability());
		return result;
	}

	private static JsonObject describeEntity(com.yeyito.littlechemistry.content.DynamicEntityProperties entity) {
		JsonObject result = new JsonObject();
		result.addProperty("movement", entity.movement().serializedName());
		result.addProperty("disposition", entity.disposition().serializedName());
		result.addProperty("width", entity.width());
		result.addProperty("height", entity.height());
		result.addProperty("eyeHeight", entity.eyeHeight());
		result.addProperty("maxHealth", entity.maxHealth());
		result.addProperty("movementSpeed", entity.movementSpeed());
		result.addProperty("attackDamage", entity.attackDamage());
		result.addProperty("armor", entity.armor());
		result.addProperty("knockbackResistance", entity.knockbackResistance());
		result.addProperty("followRange", entity.followRange());
		result.addProperty("experienceReward", entity.experienceReward());
		result.addProperty("fireImmune", entity.fireImmune());
		result.addProperty("ambientSound", entity.ambientSound().toString());
		result.addProperty("hurtSound", entity.hurtSound().toString());
		result.addProperty("deathSound", entity.deathSound().toString());
		JsonArray drops = new JsonArray();
		for (var drop : entity.drops()) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("item", drop.item().toString());
			encoded.addProperty("minimum", drop.minimum());
			encoded.addProperty("maximum", drop.maximum());
			encoded.addProperty("chance", drop.chance());
			drops.add(encoded);
		}
		result.add("drops", drops);
		return result;
	}

	private static JsonObject summarizeBehavior(String source) {
		JsonObject result = new JsonObject();
		JsonArray callbacks = new JsonArray();
		for (DynamicBehaviorCapability capability : DynamicBehaviorSource.capabilities(source)) {
			callbacks.add(capability.callbackName());
		}
		result.add("implementedCallbacks", callbacks);
		return result;
	}
}
