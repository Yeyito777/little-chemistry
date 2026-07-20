package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicContentInspectorTest {
	private static final String TEXTURE_HASH = "0".repeat(64);

	@AfterEach
	void clearCatalog() {
		DynamicContentCatalog.clear();
	}

	@Test
	void searchesBehaviorAndInspectsCompletePersistedSource() {
		String source = """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    /* lightning-resonance %s */
				    public GeneratedBehaviorImpl() {}
				}
				""".formatted("complete-source-".repeat(1_000)).strip();
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "storm_seed", "Storm Seed", 0L, TEXTURE_HASH,
				null, null, DynamicItemProperties.DEFAULT, source);
		DynamicContentCatalog.replace(List.of(definition));

		JsonObject searchArguments = new JsonObject();
		searchArguments.addProperty("query", "lightning resonance");
		searchArguments.addProperty("kind", "item");
		ContentGenerationDraft.ToolExecution search = DynamicContentInspector.search(searchArguments);

		assertTrue(search.output().get("ok").getAsBoolean());
		assertEquals(1, search.output().get("totalMatches").getAsInt());
		assertEquals("little_chemistry:storm_seed", search.output().getAsJsonArray("matches")
				.get(0).getAsJsonObject().get("contentId").getAsString());
		assertFalse(search.output().getAsJsonArray("matches").get(0).getAsJsonObject()
				.getAsJsonObject("behavior").has("javaSource"));

		JsonObject inspectArguments = new JsonObject();
		inspectArguments.addProperty("contentId", "little_chemistry:storm_seed");
		ContentGenerationDraft.ToolExecution inspection = DynamicContentInspector.inspect(inspectArguments);

		assertTrue(source.length() > 12_000);
		assertTrue(inspection.output().get("ok").getAsBoolean());
		assertEquals(source, inspection.output().getAsJsonObject("definition").get("behaviorSource").getAsString());
	}

	@Test
	void entityKindSearchesNativePropertiesAndInspectsItsVisualModel() {
		DynamicTextureSpec icon = solidTexture(16, 16, "80D0FFFF");
		DynamicTextureSpec skin = solidTexture(16, 16, "305070FF");
		java.util.EnumMap<Direction, DynamicBlockModelFace> faces = new java.util.EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) faces.put(direction, new DynamicBlockModelFace("skin", null));
		DynamicBlockModel geometry = new DynamicBlockModel(
				List.of(new DynamicBlockTexture("skin", "1".repeat(64), skin)), "skin", faces,
				List.of(new DynamicBlockModelElement(2, 0, 2, 14, 16, 14, false, faces)));
		DynamicEntityProperties properties = new DynamicEntityProperties(
				DynamicEntityMovement.FLYING, DynamicEntityDisposition.HOSTILE,
				1.0F, 1.5F, 1.0F, 24.0, 0.3, 4.0, 2.0, 0.1, 32.0, 5, false,
				Identifier.parse("minecraft:entity.zombie.ambient"),
				Identifier.parse("minecraft:entity.zombie.hurt"),
				Identifier.parse("minecraft:entity.zombie.death"), List.of());
		DynamicContentDefinition entity = new DynamicContentDefinition(
				DynamicContentType.ENTITY, "storm_wisp", "Storm Wisp", "A crackling storm spirit.",
				DynamicRarity.RARE, 0L, TEXTURE_HASH, icon, null, null,
				null, null, null, DynamicBehaviorSource.completeLegacySource(null), null,
				List.of(), null, properties, new DynamicEntityModel(geometry));
		DynamicContentCatalog.replace(List.of(entity));

		JsonObject searchArguments = new JsonObject();
		searchArguments.addProperty("query", "hostile flying");
		searchArguments.addProperty("kind", "entity");
		ContentGenerationDraft.ToolExecution search = DynamicContentInspector.search(searchArguments);

		assertTrue(search.output().get("ok").getAsBoolean(), search.output().toString());
		assertEquals(1, search.output().get("totalMatches").getAsInt());
		assertEquals("custom", search.output().getAsJsonArray("matches").get(0).getAsJsonObject()
				.getAsJsonObject("visualModel").get("profile").getAsString());

		JsonObject inspectArguments = new JsonObject();
		inspectArguments.addProperty("contentId", "storm_wisp");
		ContentGenerationDraft.ToolExecution inspection = DynamicContentInspector.inspect(inspectArguments);
		assertTrue(inspection.output().getAsJsonObject("definition").has("entity"));
		assertTrue(inspection.output().getAsJsonObject("definition").has("entityModel"));
	}

	private static DynamicTextureSpec solidTexture(int width, int height, String color) {
		return new DynamicTextureSpec(List.of(color),
				java.util.stream.Stream.generate(() -> "0".repeat(width)).limit(height).toList());
	}
}
