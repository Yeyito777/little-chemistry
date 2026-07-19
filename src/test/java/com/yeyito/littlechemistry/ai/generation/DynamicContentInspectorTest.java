package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
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
}
