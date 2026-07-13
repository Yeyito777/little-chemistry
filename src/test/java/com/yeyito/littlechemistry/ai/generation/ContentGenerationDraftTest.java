package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void armorPropertiesMustUseRequestedSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				DynamicContentType.ARMOR, "lunar boots", DynamicArmorSlot.BOOTS);
		JsonObject arguments = armorArguments("head");

		ContentGenerationDraft.ToolExecution result = draft.execute("set_armor_properties", arguments);

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("boots"));
	}

	@Test
	void armorPropertiesAcceptRequestedSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				DynamicContentType.ARMOR, "lunar boots", DynamicArmorSlot.BOOTS);

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_properties", armorArguments("boots"));

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}

	@Test
	void armorPropertiesInferSlotWhenNoneWasRequested() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar boots");

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_properties", armorArguments("boots"));

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}

	private static JsonObject armorArguments(String slot) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("slot", slot);
		arguments.addProperty("rarity", "rare");
		arguments.addProperty("foil", true);
		arguments.addProperty("enchantability", 15);
		arguments.addProperty("defense", 3.0);
		arguments.addProperty("toughness", 2.0);
		arguments.addProperty("knockbackResistance", 0.1);
		arguments.addProperty("durability", 400);
		return arguments;
	}
}
