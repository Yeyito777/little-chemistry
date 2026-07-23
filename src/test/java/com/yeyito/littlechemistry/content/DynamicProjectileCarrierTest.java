package com.yeyito.littlechemistry.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

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
		assertEquals(Set.of("base", "pulling_0", "pulling_1", "pulling_2"), modelStates(bow));
		assertEquals(Set.of("base", "pulling_0", "pulling_1", "pulling_2", "charged", "charged_firework"),
				modelStates(crossbow));
	}

	@Test
	void generatedCrossbowsRequireCompleteDistinctAuthoredFrames() throws IOException {
		DynamicItemProperties properties = projectileProperties(DynamicHeldType.CROSSBOW, 1);
		GeneratedContentSpec incompleteSpec = new GeneratedContentSpec(texture(), null, properties, null, null,
				MARKER_BEHAVIOR, null, DynamicRarity.COMMON, "A test crossbow.", List.of(),
				null, null, null, new DynamicItemVisuals(List.of(
						itemTexture(DynamicItemVisuals.PULLING_0, 1))));
		IllegalArgumentException incomplete = assertThrows(IllegalArgumentException.class,
				() -> incompleteSpec.itemVisuals().requireCompleteFor(DynamicHeldType.CROSSBOW));
		assertTrue(incomplete.getMessage().contains("requires item visual states"));

		DynamicItemVisuals visuals = new DynamicItemVisuals(List.of(
				itemTexture(DynamicItemVisuals.PULLING_0, 1),
				itemTexture(DynamicItemVisuals.PULLING_1, 2),
				itemTexture(DynamicItemVisuals.PULLING_2, 3),
				itemTexture(DynamicItemVisuals.CHARGED, 4),
				itemTexture(DynamicItemVisuals.CHARGED_FIREWORK, 5)));
		GeneratedContentSpec generated = new GeneratedContentSpec(texture(), null, properties, null, null,
				MARKER_BEHAVIOR, null, DynamicRarity.COMMON, "A test crossbow.", List.of(),
				null, null, null, visuals);
		assertEquals(5, generated.itemVisuals().states().size());
	}

	@Test
	void legacyProjectileFactoryConstructorStillProducesAStaticCompatibleSpec() {
		GeneratedContentSpec legacy = new GeneratedContentSpec(texture(), null,
				projectileProperties(DynamicHeldType.CROSSBOW, 1), null, null,
				MARKER_BEHAVIOR, null, DynamicRarity.COMMON, "A legacy crossbow.", List.of(), null, null, null);
		assertEquals(DynamicItemVisuals.NONE, legacy.itemVisuals());
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

	private static DynamicItemTexture itemTexture(String id, int marker) throws IOException {
		List<String> rows = new java.util.ArrayList<>(texture().rows());
		String row = rows.get(13);
		rows.set(13, row.substring(0, marker) + "1" + row.substring(marker + 1));
		DynamicTextureSpec texture = new DynamicTextureSpec(List.of("00000000", "804020FF", "E0C080FF"), rows);
		return new DynamicItemTexture(id, DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	private static String resource(String path) throws IOException {
		try (var input = DynamicProjectileCarrierTest.class.getResourceAsStream(path)) {
			if (input == null) throw new IOException("Missing test resource " + path);
			return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	private static Set<String> modelStates(String json) {
		Set<String> states = new java.util.HashSet<>();
		collectStates(JsonParser.parseString(json), states);
		return Set.copyOf(states);
	}

	private static void collectStates(JsonElement element, Set<String> states) {
		if (element.isJsonObject()) {
			if (element.getAsJsonObject().has("state")) {
				states.add(element.getAsJsonObject().get("state").getAsString());
			}
			element.getAsJsonObject().entrySet().forEach(entry -> collectStates(entry.getValue(), states));
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(value -> collectStates(value, states));
		}
	}
}
