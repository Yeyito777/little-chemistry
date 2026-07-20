package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeGenerationAgentTest {
	@Test
	void parsesAnAiSelectedOutputStackCount() throws IOException {
		RecipeGenerationAgent.Choice choice = RecipeGenerationAgent.parseChoice(
				choice("item", "Copper Bolts", 8), "test-model");

		assertEquals(DynamicContentType.ITEM, choice.type());
		assertEquals("Copper Bolts", choice.displayName());
		assertEquals(8, choice.outputCount());
	}

	@Test
	void rejectsFractionalAndOutOfRangeCounts() {
		assertThrows(IOException.class, () -> RecipeGenerationAgent.parseChoice(
				choice("item", "Dust", 1.5), "test-model"));
		assertThrows(IOException.class, () -> RecipeGenerationAgent.parseChoice(
				choice("block", "Packed Dust", 65), "test-model"));
	}

	@Test
	void armorMustRemainASingleNonStackableOutput() throws IOException {
		assertThrows(IOException.class, () -> RecipeGenerationAgent.parseChoice(
				choice("helmet", "Twin Crowns", 2), "test-model"));

		RecipeGenerationAgent.Choice choice = RecipeGenerationAgent.parseChoice(
				choice("helmet", "Stellar Crown", 1), "test-model");
		assertEquals(DynamicContentType.ARMOR, choice.type());
		assertEquals(DynamicArmorSlot.HEAD, choice.armorSlot());
		assertEquals(1, choice.outputCount());
	}

	@Test
	void parsesEntitySpawnerOutput() throws IOException {
		RecipeGenerationAgent.Choice choice = RecipeGenerationAgent.parseChoice(
				choice("entity", "Crystal Wisp", 2), "test-model");

		assertEquals(DynamicContentType.ENTITY, choice.type());
		assertEquals("Crystal Wisp", choice.displayName());
		assertEquals(2, choice.outputCount());
	}

	private static JsonObject choice(String kind, String name, Number count) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("kind", kind);
		arguments.addProperty("name", name);
		arguments.addProperty("count", count);
		return arguments;
	}
}
