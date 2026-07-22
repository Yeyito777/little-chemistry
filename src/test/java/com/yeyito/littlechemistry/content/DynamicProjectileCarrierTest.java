package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DynamicProjectileCarrierTest {
	private static final String MARKER_BEHAVIOR = """
			public final class GeneratedBehaviorImpl implements
			        com.yeyito.littlechemistry.behavior.DynamicBehavior {
			    public GeneratedBehaviorImpl() {}
			}
			""";

	@Test
	void projectileCarrierIdsUseNativeMinecraftClasses() {
		assertEquals(BowItem.class, DynamicBowCarrierItem.class.getSuperclass());
		assertEquals(CrossbowItem.class, DynamicCrossbowCarrierItem.class.getSuperclass());
		assertTrue(DynamicItemCarrier.class.isAssignableFrom(DynamicBowCarrierItem.class));
		assertTrue(DynamicItemCarrier.class.isAssignableFrom(DynamicCrossbowCarrierItem.class));
		assertEquals(DynamicBowCarrierItem.class, DynamicContentObjects.carrierClassFor(DynamicHeldType.BOW));
		assertEquals(DynamicCrossbowCarrierItem.class, DynamicContentObjects.carrierClassFor(DynamicHeldType.CROSSBOW));
		assertEquals(384, DynamicHeldType.BOW.nativeDurability());
		assertEquals(465, DynamicHeldType.CROSSBOW.nativeDurability());
	}

	@Test
	void newlyGeneratedProjectileWeaponsMustBeUnstackable() {
		DynamicItemProperties stackableBow = projectileProperties(DynamicHeldType.BOW, 64);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new GeneratedContentSpec(texture(), null, stackableBow, null, null,
						MARKER_BEHAVIOR, null, DynamicRarity.COMMON, "A test bow.", List.of(), null, null, null));

		assertTrue(failure.getMessage().contains("maxStack 1 and positive enchantability"));
	}

	@Test
	void itemModelsContainNativeDrawChargeAndLoadedPredicates() throws IOException {
		String bow = resource("/assets/little_chemistry/items/dynamic_bow.json");
		String crossbow = resource("/assets/little_chemistry/items/dynamic_crossbow.json");

		assertTrue(bow.contains("minecraft:using_item"));
		assertTrue(bow.contains("minecraft:use_duration"));
		assertTrue(bow.contains("minecraft:item/bow_pulling_2"));
		assertTrue(crossbow.contains("minecraft:crossbow/pull"));
		assertTrue(crossbow.contains("minecraft:charge_type"));
		assertTrue(crossbow.contains("minecraft:item/crossbow_firework"));
	}

	private static DynamicItemProperties projectileProperties(DynamicHeldType heldType, int maxStack) {
		return new DynamicItemProperties(
				DynamicItemType.ITEM, heldType, maxStack, Rarity.COMMON, false, 1, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, null, null);
	}

	private static DynamicTextureSpec texture() {
		return new DynamicTextureSpec(List.of("00000000", "804020FF", "E0C080FF"), List.of(
				"0000000000000000", "0000000001100000", "0000000012210000", "0000000122210000",
				"0000001222100000", "0000012221000000", "0000122100000000", "0001221000000000",
				"0012210000000000", "0122100000000000", "1221000000000000", "0210000000000000",
				"0100000000000000", "0000000000000000", "0000000000000000", "0000000000000000"));
	}

	private static String resource(String path) throws IOException {
		try (var input = DynamicProjectileCarrierTest.class.getResourceAsStream(path)) {
			if (input == null) throw new IOException("Missing test resource " + path);
			return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
