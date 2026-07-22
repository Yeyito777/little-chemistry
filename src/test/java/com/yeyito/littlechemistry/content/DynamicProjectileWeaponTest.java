package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicProjectileWeaponTest {
	private static final String BOW_BEHAVIOR = """
			public final class GeneratedBehaviorImpl implements
			        com.yeyito.littlechemistry.behavior.ReleaseUsingBehavior {
			    public GeneratedBehaviorImpl() {}
			    @Override
			    public boolean releaseUsing(
			            com.yeyito.littlechemistry.behavior.DynamicItemUsingContext context) {
			        return context.chargeProgress() > 0.0F;
			    }
			}
				""";
	private static final String MARKER_BEHAVIOR = """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				}
				""";

	@Test
	void ordinaryProjectileWeaponsMayBeDurableButNotStackable() {
		DynamicItemProperties bow = bowProperties(384);
		assertEquals(384, bow.durability());
		assertThrows(IllegalArgumentException.class, () -> new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.BOW, 2, Rarity.UNCOMMON, false, 1, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				384, 0, 0, null, null));
	}

	@Test
	void nativeProjectileMechanicsDoNotRequireGeneratedReimplementation() throws IOException {
		GeneratedContentSpec bow = new GeneratedContentSpec(
				texture("5"), null, bowProperties(384), null, null, MARKER_BEHAVIOR, null,
				DynamicRarity.UNCOMMON, "Vanilla shoots; generated behavior is optional.", List.of(),
				null, null, null, bowVisuals());

		assertNotNull(bow);
		assertTrue(com.yeyito.littlechemistry.behavior.DynamicBehaviorSource.capabilities(
				bow.behaviorSource()).isEmpty());
	}

	@Test
	void bowVisualStatesAndLifecycleSurviveCatalogRoundTrip() throws IOException {
		DynamicTextureSpec base = texture("5");
		DynamicItemVisuals visuals = bowVisuals();
		DynamicItemProperties properties = bowProperties(384);
		GeneratedContentSpec generated = new GeneratedContentSpec(
				base, null, properties, null, null, BOW_BEHAVIOR, null, DynamicRarity.UNCOMMON,
				"A durable bow with native draw states.", List.of(), null, null, null, visuals);
		assertNotNull(DynamicBehaviorCompiler.compile(generated.behaviorSource()));

		String baseHash = DynamicTextureAsset.sha256(base.renderPng());
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "stateful_bow", "Stateful Bow", generated.description(),
				DynamicRarity.UNCOMMON, 4L, baseHash, base, null, null,
				null, properties, null, BOW_BEHAVIOR, null, List.of(), null, null, null, visuals);
		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 2, List.of(definition))).definitions().getFirst();

		assertEquals(384, decoded.item().durability());
		assertEquals(visuals, decoded.itemVisuals());
		assertTrue(decoded.renderTextureHashes().contains(baseHash));
		assertTrue(decoded.renderTextureHashes().containsAll(visuals.textureHashes()));
	}

	@Test
	void crossbowRequiresSeparateArrowAndFireworkChargedVisuals() throws IOException {
		DynamicItemVisuals incomplete = new DynamicItemVisuals(List.of(
				itemTexture(DynamicItemVisuals.PULLING_0, texture("5")),
				itemTexture(DynamicItemVisuals.PULLING_1, texture("6")),
				itemTexture(DynamicItemVisuals.PULLING_2, texture("7")),
				itemTexture(DynamicItemVisuals.CHARGED, texture("8"))));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> incomplete.requireCompleteFor(DynamicHeldType.CROSSBOW));

		assertTrue(error.getMessage().contains(DynamicItemVisuals.CHARGED_FIREWORK), error.getMessage());
	}

	@Test
	void bowAndCrossbowModelsDispatchNativeVisualStates() throws IOException {
		String bow = resource("/assets/little_chemistry/items/dynamic_bow.json");
		String crossbow = resource("/assets/little_chemistry/items/dynamic_crossbow.json");
		assertTrue(bow.contains("minecraft:use_duration"));
		assertTrue(bow.contains("\"state\": \"pulling_2\""));
		assertTrue(crossbow.contains("minecraft:crossbow/pull"));
		assertTrue(crossbow.contains("minecraft:charge_type"));
		assertTrue(crossbow.contains("\"state\": \"charged_firework\""));
	}

	private static DynamicItemProperties bowProperties(int durability) {
		return new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.BOW, 1, Rarity.UNCOMMON, false, 18, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				durability, 0, 0, null, null);
	}

	private static DynamicItemVisuals bowVisuals() throws IOException {
		return new DynamicItemVisuals(List.of(
				itemTexture(DynamicItemVisuals.PULLING_0, texture("6")),
				itemTexture(DynamicItemVisuals.PULLING_1, texture("7")),
				itemTexture(DynamicItemVisuals.PULLING_2, texture("8"))));
	}

	private static DynamicItemTexture itemTexture(String id, DynamicTextureSpec texture) throws IOException {
		return new DynamicItemTexture(id, DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	private static DynamicTextureSpec texture(String center) {
		return new DynamicTextureSpec(List.of("00000000", "241507FF", "8A5A2BFF", "E8C070FF",
				"4A3010FF", "705020FF", "986830FF", "B88040FF", "D8A858FF"), List.of(
				"0000000000000000", "0000000001100000", "0000000012210000", "0000000122210000",
				"0000001222100000", "0000012221000000", "0000122" + center + "00000000",
				"000122" + center + "100000000", "00122" + center + "1000000000", "0122" + center + "10000000000",
				"122" + center + "100000000000", "0221000000000000", "0210000000000000", "0100000000000000",
				"0000000000000000", "0000000000000000"));
	}

	private static String resource(String path) throws IOException {
		try (var input = DynamicProjectileWeaponTest.class.getResourceAsStream(path)) {
			if (input == null) throw new IOException("Missing test resource " + path);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
