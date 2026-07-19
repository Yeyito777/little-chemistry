package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBreakingPower;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicFoodProperties;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicMaterial;
import com.yeyito.littlechemistry.content.DynamicParticleEmitter;
import com.yeyito.littlechemistry.content.DynamicParticleType;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicTool;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicContentAiDescriptionTest {
	private static final String TEXTURE_HASH = "0".repeat(64);

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void exposesAllBlockGameplayProperties() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.CRYSTAL, 7.5F, DynamicTool.PICKAXE, true, DynamicBlockShape.STAR,
				11, 6, 13, true,
				List.of(new DynamicParticleEmitter(DynamicParticleType.GLOW, 0.1, 2, 0.05, true)));
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "charged_crystal", "Charged Crystal", 0L, TEXTURE_HASH,
				null, block, null, null, DynamicBehaviorSource.completeLegacySource(null));

		JsonObject description = DynamicContentAiDescription.describe(definition);
		JsonObject properties = description.getAsJsonObject("gameplayProperties");

		assertEquals("little_chemistry:charged_crystal", description.get("contentId").getAsString());
		assertEquals("crystal", properties.get("material").getAsString());
		assertEquals(7.5F, properties.get("hardness").getAsFloat());
		assertEquals("pickaxe", properties.get("preferredTool").getAsString());
		assertTrue(properties.get("requiresCorrectTool").getAsBoolean());
		assertEquals("star", properties.get("shape").getAsString());
		assertEquals(11, properties.get("redstonePower").getAsInt());
		assertEquals(6, properties.get("comparatorPower").getAsInt());
		assertEquals(13, properties.get("lightLevel").getAsInt());
		assertTrue(properties.get("visuallyEmissive").getAsBoolean());
		assertEquals("glow", properties.getAsJsonArray("particles").get(0).getAsJsonObject()
				.get("type").getAsString());
		assertEquals(0, description.getAsJsonObject("behavior").getAsJsonArray("implementedCallbacks").size());
	}

	@Test
	void exposesItemFoodAndCodeBehavior() {
		DynamicFoodProperties food = new DynamicFoodProperties(8, 6.5F, 1.2F, true, List.of());
		DynamicItemProperties item = new DynamicItemProperties(
				DynamicItemType.FOOD, DynamicHeldType.REGULAR, 16, Rarity.EPIC, true, 12, 2.5,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, food, null);
		String source = """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.DynamicBehavior,
				        com.yeyito.littlechemistry.behavior.UseAirBehavior {
				    public GeneratedBehaviorImpl() {}
				    public net.minecraft.world.InteractionResult useAir(
				            com.yeyito.littlechemistry.behavior.DynamicItemUseContext context) {
				        return net.minecraft.world.InteractionResult.SUCCESS;
				    }
				}
				""";
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "phase_berry", "Phase Berry",
				"A berry that flickers between nearby moments.", DynamicRarity.MYTHICAL,
				0L, TEXTURE_HASH, null, null, null, null, item, null, source, null);

		JsonObject description = DynamicContentAiDescription.describe(definition);
		JsonObject properties = description.getAsJsonObject("gameplayProperties");
		JsonObject behavior = description.getAsJsonObject("behavior");

		assertEquals("food", properties.get("itemType").getAsString());
		assertEquals(16, properties.get("maxStack").getAsInt());
		assertEquals("consume", properties.get("craftingUse").getAsString());
		assertEquals("mythical", description.get("rarity").getAsString());
		assertEquals("A berry that flickers between\nnearby moments.", description.get("description").getAsString());
		assertTrue(properties.get("foil").getAsBoolean());
		assertEquals(12, properties.get("enchantability").getAsInt());
		assertEquals(2.5, properties.get("reach").getAsDouble());
		assertEquals(8, properties.getAsJsonObject("food").get("hunger").getAsInt());
		assertEquals("useAir", behavior.getAsJsonArray("implementedCallbacks").get(0).getAsString());
		assertEquals(1, behavior.getAsJsonArray("implementedCallbacks").size());
		assertTrue(behavior.get("javaSourceExcerpt").getAsString().contains("GeneratedBehaviorImpl"));
		assertFalse(behavior.get("javaSourceTruncated").getAsBoolean());
	}
}
