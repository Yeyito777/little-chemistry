package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicNativeCapabilityTest {
	@Test
	void everyStatefulNativeArchetypeRequiresItsAuthoredModelState() {
		for (DynamicHeldType type : List.of(DynamicHeldType.ROD, DynamicHeldType.BOW,
				DynamicHeldType.CROSSBOW, DynamicHeldType.SHIELD, DynamicHeldType.TRIDENT)) {
			assertTrue(type.requiresVisualStates(), type.name());
			assertThrows(IllegalArgumentException.class,
					() -> DynamicItemVisuals.NONE.requireCompleteFor(type), type.name());
		}
	}

	@Test
	void nativeWearArchetypesCannotSilentlyBecomeImmortal() {
		for (DynamicHeldType type : List.of(DynamicHeldType.ROD, DynamicHeldType.BOW,
				DynamicHeldType.CROSSBOW, DynamicHeldType.MACE, DynamicHeldType.SPEAR,
				DynamicHeldType.SHIELD, DynamicHeldType.TRIDENT)) {
			assertTrue(type.requiresDurability(), type.name());
		}
		assertFalse(DynamicHeldType.REGULAR.requiresDurability());
	}

	@Test
	void generatedConsumablesSupportNativeDrinkPresentation() {
		DynamicFoodProperties drink = new DynamicFoodProperties(
				2, 0.5F, 1.2F, true, List.of(), DynamicConsumeStyle.DRINK);

		assertEquals(ItemUseAnimation.DRINK, drink.style().builder().build().animation());
	}

	@Test
	void generatedChestEquipmentMayOptIntoGliding() {
		DynamicArmorProperties glider = new DynamicArmorProperties(
				DynamicArmorSlot.CHEST, Rarity.RARE, false, 12,
				6.0, 1.0, 0.0, 500, true);

		assertTrue(glider.glider());
	}

	@Test
	void ordinaryNativeProfilesMayDefineMeleeAttributesWithoutPretendingToBeMiningTools() {
		DynamicItemProperties spear = new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.SPEAR, 1, Rarity.RARE, false, 12, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 9.0, 1.2,
				500, 0, 1, null, null);

		assertEquals(9.0, spear.attackDamage());
		assertEquals(DynamicTool.NONE, spear.tool());
	}

	@Test
	void itemArtworkMayUseRealTranslucentPixels() {
		DynamicTextureSpec texture = new DynamicTextureSpec(
				List.of("00000000", "80C0FF80"), List.of(
				"0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000",
				"0000000110000000", "0000001111000000", "0000011111100000", "0000011111100000",
				"0000011111100000", "0000011111100000", "0000001111000000", "0000000110000000",
				"0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000"));

		assertTrue(texture.hasTranslucentAlpha());
	}
}
