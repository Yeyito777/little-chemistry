package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorProperties;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicFoodEffect;
import com.yeyito.littlechemistry.content.DynamicFoodProperties;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicParticleEmitter;
import com.yeyito.littlechemistry.content.DynamicPlacementProperties;

import java.util.List;
import java.util.regex.Pattern;

/** Produces a compact, complete gameplay view of a generated crafting ingredient for the AI agents. */
public final class DynamicContentAiDescription {
	private static final int MAX_BEHAVIOR_SOURCE_CHARS = 12_000;
	private static final int BEHAVIOR_SOURCE_TAIL_CHARS = 4_000;
	private static final List<String> BEHAVIOR_CALLBACKS = List.of(
			"useAir", "useOnBlock", "interactLivingEntity", "inventoryTick", "postHurtEnemy", "mineBlock",
			"finishUsing", "crafted", "usePlacedBlock", "attackPlacedBlock", "placedBlock", "brokenBlock",
			"stepOnBlock", "fallOnBlock", "entityInsideBlock", "randomTickBlock", "scheduledTickBlock",
			"neighborChangedBlock", "projectileHitBlock"
	);

	private DynamicContentAiDescription() {
	}

	public static JsonObject describe(DynamicContentDefinition definition) {
		JsonObject result = new JsonObject();
		result.addProperty("contentId", LittleChemistry.MOD_ID + ":" + definition.name());
		result.addProperty("type", definition.type().serializedName());
		result.addProperty("name", definition.name());
		result.addProperty("displayName", definition.displayName());
		result.add("gameplayProperties", switch (definition.type()) {
			case BLOCK -> describeBlock(definition.block());
			case ITEM -> describeItem(definition.item());
			case ARMOR -> describeArmor(definition.armor());
		});
		result.add("behavior", describeBehavior(definition.behaviorSource()));
		return result;
	}

	private static JsonObject describeBlock(DynamicBlockProperties block) {
		JsonObject result = new JsonObject();
		result.addProperty("material", block.material().getSerializedName());
		result.addProperty("hardness", block.hardness());
		result.addProperty("preferredTool", block.preferredTool().serializedName());
		result.addProperty("requiresCorrectTool", block.requiresCorrectTool());
		result.addProperty("shape", block.shape().serializedName());
		result.addProperty("redstonePower", block.redstonePower());
		result.addProperty("comparatorPower", block.comparatorPower());
		result.addProperty("lightLevel", block.lightLevel());
		result.addProperty("visuallyEmissive", block.visuallyEmissive());
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
		result.add("particles", particles);
		return result;
	}

	private static JsonObject describeItem(DynamicItemProperties item) {
		JsonObject result = new JsonObject();
		result.addProperty("itemType", item.itemType().serializedName());
		result.addProperty("heldType", item.heldType().serializedName());
		result.addProperty("maxStack", item.maxStack());
		result.addProperty("rarity", item.rarity().getSerializedName());
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
		result.addProperty("rarity", armor.rarity().getSerializedName());
		result.addProperty("foil", armor.foil());
		result.addProperty("enchantability", armor.enchantability());
		result.addProperty("defense", armor.defense());
		result.addProperty("toughness", armor.toughness());
		result.addProperty("knockbackResistance", armor.knockbackResistance());
		result.addProperty("durability", armor.durability());
		return result;
	}

	private static JsonObject describeBehavior(String source) {
		JsonObject result = new JsonObject();
		JsonArray callbacks = new JsonArray();
		for (String callback : BEHAVIOR_CALLBACKS) {
			if (Pattern.compile("\\b" + callback + "\\s*\\(").matcher(source).find()) callbacks.add(callback);
		}
		result.add("implementedCallbacks", callbacks);
		boolean truncated = source.length() > MAX_BEHAVIOR_SOURCE_CHARS;
		result.addProperty("javaSourceExcerpt", truncated ? abbreviateSource(source) : source);
		result.addProperty("javaSourceTruncated", truncated);
		return result;
	}

	private static String abbreviateSource(String source) {
		int headLength = MAX_BEHAVIOR_SOURCE_CHARS - BEHAVIOR_SOURCE_TAIL_CHARS;
		return source.substring(0, headLength)
				+ "\n/* ... source excerpt truncated for crafting analysis ... */\n"
				+ source.substring(source.length() - BEHAVIOR_SOURCE_TAIL_CHARS);
	}
}
