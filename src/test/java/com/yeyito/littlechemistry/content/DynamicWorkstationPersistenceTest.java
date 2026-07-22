package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicWorkstationPersistenceTest {
	private static final String TEXTURE_HASH = "0".repeat(64);
	private static final String WORKSTATION_BEHAVIOR = """
			public final class GeneratedBehaviorImpl implements
					com.yeyito.littlechemistry.behavior.DynamicBehavior,
					com.yeyito.littlechemistry.behavior.WorkstationBehavior,
					com.yeyito.littlechemistry.behavior.WorkstationTickBehavior {
				public GeneratedBehaviorImpl() {}
				public com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest createWorkstationRecipe(
						com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
					return com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest.builder()
							.consume("input", 1).build();
				}
				public void workstationTick(com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
					if (context.recipeStatus() == com.yeyito.littlechemistry.behavior.WorkstationRecipeStatus.READY)
						context.tryCompleteRecipe();
				}
			}
			""";

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void currentCatalogRoundTripPreservesOptionalWorkstation() {
		DynamicWorkstationSpec workstation = DynamicWorkstationSpecTest.workstation();
		DynamicContentDefinition definition = blockDefinition(workstation);

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 7, List.of(definition));
		DynamicContentJson.Decoded catalog = DynamicContentJson.decode(encoded);

		assertEquals(21, DynamicContentJson.CURRENT_FORMAT);
		assertEquals(workstation, catalog.definitions().getFirst().workstation());
		assertEquals("Separates mixed materials through controlled rotational force over 200 Minecraft ticks.",
				catalog.definitions().getFirst().workstation().processDescription());
	}

	@Test
	void formatSeventeenCatalogMigratesToOrdinaryBlock() {
		byte[] current = DynamicContentJson.encode(UUID.randomUUID(), 2, List.of(blockDefinition(null)));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 17);
		legacy.getAsJsonArray("definitions").get(0).getAsJsonObject().remove("workstation");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertNull(decoded.workstation());
	}

	@Test
	void workstationCapabilityIsRejectedOnNonBlockDefinitions() {
		assertThrows(IllegalArgumentException.class, () -> new DynamicContentDefinition(
				DynamicContentType.ITEM, "bad_station", "Bad Station", "", DynamicRarity.COMMON,
				0L, TEXTURE_HASH, null, null, null, null, DynamicItemProperties.DEFAULT, null,
				DynamicBehaviorSource.completeLegacySource(null), null, List.of(),
				DynamicWorkstationSpecTest.workstation()));
	}

	@Test
	void generatedBlockSpecCarriesWorkstationWithoutChangingOldConstructionRules() {
		DynamicTextureSpec texture = new DynamicTextureSpec(List.of("405060FF"),
				java.util.stream.Stream.generate(() -> "0".repeat(16)).limit(16).toList());
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) faces.put(direction, new DynamicBlockModelFace("main", null));
		DynamicBlockModel model = new DynamicBlockModel(
				List.of(new DynamicBlockTexture("main", TEXTURE_HASH, texture)), "main", faces, List.of());
		DynamicWorkstationSpec workstation = DynamicWorkstationSpecTest.workstation();

		GeneratedContentSpec generated = new GeneratedContentSpec(
				texture, DynamicBlockProperties.DEFAULT, null, null, null,
				WORKSTATION_BEHAVIOR, model, DynamicRarity.COMMON, "", List.of(), workstation);

		assertEquals(workstation, generated.workstation());
		assertThrows(IllegalArgumentException.class, () -> new GeneratedContentSpec(
				texture, null, DynamicItemProperties.DEFAULT, null, null,
				DynamicBehaviorSource.completeLegacySource(null), null, DynamicRarity.COMMON, "", List.of(), workstation));
	}

	private static DynamicContentDefinition blockDefinition(DynamicWorkstationSpec workstation) {
		return new DynamicContentDefinition(
				DynamicContentType.BLOCK, "centrifuge", "Centrifuge", "Separates mixed materials.",
				DynamicRarity.COMMON, 0L, TEXTURE_HASH, null, null, null,
				DynamicBlockProperties.DEFAULT, null, null,
				workstation == null ? DynamicBehaviorSource.completeLegacySource(null) : WORKSTATION_BEHAVIOR,
				null, List.of(), workstation);
	}
}
