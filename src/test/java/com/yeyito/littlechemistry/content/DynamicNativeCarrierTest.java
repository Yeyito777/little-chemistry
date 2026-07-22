package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TridentItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicNativeCarrierTest {
	@Test
	void classSensitiveProfilesUseNativeMinecraftSuperclasses() {
		assertCarrier(DynamicHeldType.BOW, DynamicTool.NONE, BowItem.class);
		assertCarrier(DynamicHeldType.CROSSBOW, DynamicTool.NONE, CrossbowItem.class);
		assertCarrier(DynamicHeldType.ROD, DynamicTool.NONE, FishingRodItem.class);
		assertCarrier(DynamicHeldType.MACE, DynamicTool.NONE, MaceItem.class);
		assertCarrier(DynamicHeldType.SHIELD, DynamicTool.NONE, ShieldItem.class);
		assertCarrier(DynamicHeldType.TRIDENT, DynamicTool.NONE, TridentItem.class);
		assertCarrier(DynamicHeldType.TOOL, DynamicTool.AXE, AxeItem.class);
		assertCarrier(DynamicHeldType.TOOL, DynamicTool.SHOVEL, ShovelItem.class);
		assertCarrier(DynamicHeldType.TOOL, DynamicTool.HOE, HoeItem.class);
		assertCarrier(DynamicHeldType.REGULAR, DynamicTool.AXE, AxeItem.class);

		// 26.2's ordinary spears and pickaxes are component-driven vanilla Items, not subclasses.
		assertEquals(DynamicCarrierItem.class,
				DynamicContentObjects.carrierClassFor(DynamicHeldType.SPEAR, DynamicTool.NONE));
		assertEquals(DynamicCarrierItem.class,
				DynamicContentObjects.carrierClassFor(DynamicHeldType.TOOL, DynamicTool.PICKAXE));
		assertEquals(DynamicCarrierItem.class,
				DynamicContentObjects.carrierClassFor(DynamicHeldType.TOOL, DynamicTool.NONE));
	}

	@Test
	void everyNativeSubclassStillOptsIntoGeneratedHookDispatch() {
		for (Class<? extends Item> carrier : java.util.List.<Class<? extends Item>>of(
				DynamicBowCarrierItem.class,
				DynamicCrossbowCarrierItem.class,
				DynamicFishingRodCarrierItem.class,
				DynamicMaceCarrierItem.class,
				DynamicShieldCarrierItem.class,
				DynamicTridentCarrierItem.class,
				DynamicAxeCarrierItem.class,
				DynamicShovelCarrierItem.class,
				DynamicHoeCarrierItem.class)) {
			assertTrue(DynamicItemCarrier.class.isAssignableFrom(carrier), carrier.getName());
		}
	}

	@Test
	void oldCatchAllToolStacksHaveNativeUseOnCompatibility() {
		assertEquals(DynamicAxeCarrierItem.class,
				DynamicContentObjects.compatibilityToolClassFor(tool(DynamicTool.AXE)));
		assertEquals(DynamicShovelCarrierItem.class,
				DynamicContentObjects.compatibilityToolClassFor(tool(DynamicTool.SHOVEL)));
		assertEquals(DynamicHoeCarrierItem.class,
				DynamicContentObjects.compatibilityToolClassFor(tool(DynamicTool.HOE)));
		assertNull(DynamicContentObjects.compatibilityToolClassFor(tool(DynamicTool.PICKAXE)));
		assertNull(DynamicContentObjects.compatibilityToolClassFor(DynamicItemProperties.DEFAULT));
	}

	@Test
	void nativeStateModelsAndClassificationTagsArePackaged() throws IOException {
		assertResourceContains("/assets/little_chemistry/items/dynamic_rod.json", "minecraft:fishing_rod/cast");
		assertResourceContains("/assets/little_chemistry/items/dynamic_shield.json", "minecraft:item/shield_blocking");
		assertResourceContains("/assets/little_chemistry/items/dynamic_trident.json", "minecraft:item/trident_throwing");
		assertResourceContains("/data/minecraft/tags/item/axes.json", "little_chemistry:dynamic_axe");
		assertResourceContains("/data/minecraft/tags/item/shovels.json", "little_chemistry:dynamic_shovel");
		assertResourceContains("/data/minecraft/tags/item/hoes.json", "little_chemistry:dynamic_hoe");
		assertResourceContains("/data/minecraft/tags/item/spears.json", "little_chemistry:dynamic_spear");
		assertResourceContains("/data/minecraft/tags/item/enchantable/trident.json", "little_chemistry:dynamic_trident");
		assertResourceContains("/data/minecraft/tags/item/enchantable/weapon.json", "little_chemistry:dynamic_mace");
	}

	private static void assertCarrier(DynamicHeldType heldType, DynamicTool tool,
			Class<? extends Item> vanillaClass) {
		Class<? extends Item> carrier = DynamicContentObjects.carrierClassFor(heldType, tool);
		assertTrue(vanillaClass.isAssignableFrom(carrier), carrier + " should extend " + vanillaClass);
	}

	private static DynamicItemProperties tool(DynamicTool tool) {
		return new DynamicItemProperties(DynamicItemType.TOOL, DynamicHeldType.TOOL, 1,
				Rarity.COMMON, false, 0, 0.0, tool, DynamicBreakingPower.WOOD,
				2.0F, 4.0, 1.0, 64, 1, 1, null, null);
	}

	private static void assertResourceContains(String path, String expected) throws IOException {
		try (var input = DynamicNativeCarrierTest.class.getResourceAsStream(path)) {
			if (input == null) throw new IOException("Missing test resource " + path);
			String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(content.contains(expected), path + " should contain " + expected);
		}
	}
}
