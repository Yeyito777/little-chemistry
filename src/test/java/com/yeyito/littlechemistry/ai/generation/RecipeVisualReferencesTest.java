package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeVisualReferencesTest {
	@Test
	void exactReferenceBytesProduceAStableValidatedRequestDigest() {
		var leather = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:leather"}]}
				""").getAsJsonObject();
		var string = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:string"}]}
				""").getAsJsonObject();

		RecipeVisualReferences.Bundle leatherReferences = RecipeVisualReferences.forRequest(leather);
		String leatherDigest = leatherReferences.digest();
		String stringDigest = RecipeVisualReferences.forRequest(string).digest();
		assertFalse(leatherReferences.promptSection().contains("minecraft:item/leather_boots"));
		assertNotEquals(leatherDigest, stringDigest);

		leather.addProperty("visualReferenceDigest", leatherDigest);
		assertDoesNotThrow(() -> RecipeVisualReferences.forRequest(leather));
		leather.addProperty("visualReferenceDigest", stringDigest);
		assertThrows(IllegalArgumentException.class, () -> RecipeVisualReferences.forRequest(leather));
	}

	@Test
	void anExplicitEquippableAssetSuppliesEveryAssociatedTextualLayer() {
		var recipe = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:golden_nautilus_armor",
				          "equipmentAssetId":"minecraft:gold"}]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();

		assertTrue(section.contains("minecraft:item/golden_nautilus_armor"));
		assertTrue(section.contains("minecraft:entity/equipment/humanoid/gold"));
		assertTrue(section.contains("minecraft:entity/equipment/nautilus_body/gold"));
		assertTrue(section.contains("\"tiles\""));
	}

	@Test
	void minecraftItemDefinitionGraphSuppliesBlockAndAnimatedStateTextures() {
		var recipe = JsonParser.parseString("""
				{"grid":[
				  {"itemId":"minecraft:furnace"},
				  {"itemId":"minecraft:fishing_rod"},
				  {"itemId":"minecraft:bundle"},
				  {"itemId":"minecraft:stick","itemModelId":"minecraft:brush"}
				]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();

		assertTrue(section.contains("minecraft:block/furnace_front"));
		assertTrue(section.contains("minecraft:item/fishing_rod_cast"));
		assertTrue(section.contains("minecraft:item/bundle_open_front"));
		assertTrue(section.contains("minecraft:item/bundle_open_back"));
		assertTrue(section.contains("\"itemModelId\": \"minecraft:brush\""));
		assertTrue(section.contains("minecraft:item/brush"));
	}
}
