package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentGenerationDraftTest {
	@Test
	void ordinaryItemCanRequestToolHeldType() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "magic staff");
		JsonObject arguments = new JsonObject();
		arguments.addProperty("itemType", "item");
		arguments.addProperty("heldType", "tool");
		arguments.addProperty("maxStack", 16);
		arguments.addProperty("rarity", "uncommon");
		arguments.addProperty("foil", false);
		arguments.addProperty("enchantability", 0);
		arguments.addProperty("reach", 0.0);
		arguments.addProperty("placeable", false);

		ContentGenerationDraft.ToolExecution result = draft.execute("set_item_properties", arguments);

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}
}
