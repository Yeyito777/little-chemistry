package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	@Test
	void armorDraftRequiresAndAcceptsSeparateDisplayTexture() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar boots");
		JsonObject empty = new JsonObject();

		ContentGenerationDraft.ToolExecution before = draft.execute("inspect_draft", empty);
		ContentGenerationDraft.ToolExecution set = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());
		ContentGenerationDraft.ToolExecution after = draft.execute("inspect_draft", empty);

		assertTrue(before.output().getAsJsonArray("missing").toString().contains("armorDisplayTexture"));
		assertTrue(set.output().get("ok").getAsBoolean(), set.output().toString());
		assertFalse(after.output().getAsJsonArray("missing").toString().contains("armorDisplayTexture"));
	}

	@Test
	void nonArmorCannotSetArmorDisplayTexture() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "lunar shard");

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("not creating armor"));
	}

	@Test
	void armorDisplayTextureMustMatchPropertiesSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar helmet");
		draft.execute("set_armor_properties", armorArguments("head"));

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("head"));
	}

	@Test
	void completedNativeItemSubmitsWithoutBehaviorClass() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		draft.execute("set_texture", itemTextureArguments());
		draft.execute("set_item_properties", ordinaryItemArguments());
		draft.execute("set_behavior_plan", behaviorPlanArguments("native"));

		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());
		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());

		assertFalse(inspected.output().get("behaviorRequired").getAsBoolean(), inspected.output().toString());
		assertEquals("native", inspected.output().get("behaviorMode").getAsString());
		assertTrue(inspected.output().get("complete").getAsBoolean(), inspected.output().toString());
		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertNull(submitted.submitted().behaviorSource());
	}

	@Test
	void optionalBehaviorMustCompileWhenSourceWasSetAndCanBeCleared() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		draft.execute("set_behavior_plan", behaviorPlanArguments("custom"));
		JsonObject source = new JsonObject();
		source.addProperty("source", "public final class GeneratedBehaviorImpl {}");
		draft.execute("set_behavior_source", source);

		ContentGenerationDraft.ToolExecution withSource = draft.execute("inspect_draft", new JsonObject());
		draft.execute("clear_behavior", new JsonObject());
		ContentGenerationDraft.ToolExecution cleared = draft.execute("inspect_draft", new JsonObject());

		assertTrue(withSource.output().getAsJsonArray("missing").toString().contains("behaviorCompilation"));
		assertFalse(cleared.output().getAsJsonArray("missing").toString().contains("behavior"));
		assertEquals("none", cleared.output().get("behaviorStatus").getAsString());
		assertEquals("native", cleared.output().get("behaviorMode").getAsString());
	}

	@Test
	void submitRequiresAnExplicitBehaviorPlan() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		draft.execute("set_texture", itemTextureArguments());
		draft.execute("set_item_properties", ordinaryItemArguments());

		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertFalse(inspected.output().get("complete").getAsBoolean(), inspected.output().toString());
		assertTrue(inspected.output().getAsJsonArray("missing").toString().contains("behaviorPlan"));
		assertEquals("undecided", inspected.output().get("behaviorMode").getAsString());
	}

	@Test
	void customBehaviorPlanRequiresSource() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "fairy wand");
		draft.execute("set_behavior_plan", behaviorPlanArguments("custom"));

		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertTrue(inspected.output().getAsJsonArray("missing").toString().contains("behaviorSource"));
		assertTrue(inspected.output().get("behaviorRequired").getAsBoolean());
		assertEquals("custom", inspected.output().get("behaviorMode").getAsString());
	}

	@Test
	void behaviorSourceIsRejectedWithoutCustomPlan() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		JsonObject source = new JsonObject();
		source.addProperty("source", "public final class GeneratedBehaviorImpl {}");

		ContentGenerationDraft.ToolExecution result = draft.execute("set_behavior_source", source);

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertEquals("CUSTOM_BEHAVIOR_NOT_PLANNED", result.output().get("code").getAsString());
	}

	@Test
	void minimalBehaviorApiExampleCompiles() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "fairy wand");
		draft.execute("set_behavior_plan", behaviorPlanArguments("custom"));
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_behavior_api", new JsonObject());
		JsonObject source = new JsonObject();
		source.addProperty("source", inspected.output().get("example").getAsString());
		draft.execute("set_behavior_source", source);

		ContentGenerationDraft.ToolExecution compiled = draft.execute("compile_behavior", new JsonObject());

		assertTrue(compiled.output().get("ok").getAsBoolean(), compiled.output().toString());
	}

	@Test
	void overworldVegetationProfileAddsGrassCompatibleSupport() {
		java.util.List<String> supports = ContentGenerationDraft.resolvePlacementSupports(
				"overworld_vegetation", java.util.List.of("minecraft:sand"));

		assertEquals(java.util.List.of("#minecraft:supports_vegetation", "minecraft:sand"), supports);
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

	private static JsonObject armorDisplayTextureArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("slot", "boots");
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("305080FF");
		palette.add("D0F0FFFF");
		arguments.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 32; y++) {
			rows.add(y < 16 ? "1".repeat(32) + "0".repeat(32) : "12".repeat(16) + "0".repeat(32));
		}
		arguments.add("rows", rows);
		return arguments;
	}

	private static JsonObject ordinaryItemArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("itemType", "item");
		arguments.addProperty("heldType", "regular");
		arguments.addProperty("maxStack", 64);
		arguments.addProperty("rarity", "common");
		arguments.addProperty("foil", false);
		arguments.addProperty("enchantability", 0);
		arguments.addProperty("reach", 0.0);
		arguments.addProperty("placeable", false);
		return arguments;
	}

	private static JsonObject itemTextureArguments() {
		JsonObject arguments = new JsonObject();
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("202020FF");
		palette.add("E0F0FFFF");
		arguments.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 16; y++) rows.add("0120120120120120");
		arguments.add("rows", rows);
		return arguments;
	}

	private static JsonObject behaviorPlanArguments(String mode) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("mode", mode);
		arguments.addProperty("reason", mode.equals("custom")
				? "The requested item has an active ability beyond native properties."
				: "Native properties completely express this passive item.");
		return arguments;
	}
}
