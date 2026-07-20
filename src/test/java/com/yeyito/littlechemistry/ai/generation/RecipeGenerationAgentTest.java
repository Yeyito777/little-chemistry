package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicWorkstationRecipeDataSchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	void parsesGeneratedEntitySpawnerOutput() throws IOException {
		RecipeGenerationAgent.Choice choice = RecipeGenerationAgent.parseChoice(
				choice("entity", "Crystal Wisp", 3), "test-model");

		assertEquals(DynamicContentType.ENTITY, choice.type());
		assertEquals("Crystal Wisp", choice.displayName());
		assertEquals(3, choice.outputCount());
	}

	@Test
	void workstationChoiceRequiresAndPreservesRecipeData() throws IOException {
		JsonObject arguments = choice("block", "Pressure Crystal", 2);
		JsonObject recipeData = new JsonObject();
		recipeData.addProperty("duration_ticks", 240);
		arguments.add("recipeData", recipeData);

		RecipeGenerationAgent.Choice choice = RecipeGenerationAgent.parseChoice(arguments, "test-model", true);

		assertEquals(240, choice.recipeData().get("duration_ticks").getAsInt());
		assertThrows(IOException.class, () -> RecipeGenerationAgent.parseChoice(
				choice("item", "Missing Data", 1), "test-model", true));
	}

	@Test
	void workstationSelectorReturnsSchemaAndCapacityErrorsToTheToolLoopForRepair() throws Exception {
		JsonObject schema = JsonParser.parseString("""
				{
				  "type":"object",
				  "properties":{"duration_ticks":{"type":"integer","minimum":1,"maximum":1200}},
				  "required":["duration_ticks"],
				  "additionalProperties":false
				}
				""").getAsJsonObject();
		JsonObject context = JsonParser.parseString("""
				{
				  "recipeType":"workstation",
				  "workstation":{"primaryOutput":{"id":"result","capacity":8}},
				  "workstationContext":{"temperature":"descriptive"}
				}
				""").getAsJsonObject();
		AtomicInteger rounds = new AtomicInteger();
		AtomicReference<String> prompt = new AtomicReference<>();
		AtomicReference<Integer> advertisedMaximum = new AtomicReference<>();
		List<JsonArray> receivedHistories = new ArrayList<>();
		RecipeGenerationAgent.ToolRoundRunner runner = (instructions, tools, input) -> {
			prompt.set(instructions);
			advertisedMaximum.set(tools.get(0).getAsJsonObject().getAsJsonObject("parameters")
					.getAsJsonObject("properties").getAsJsonObject("count").get("maximum").getAsInt());
			receivedHistories.add(input.deepCopy());
			int round = rounds.getAndIncrement();
			JsonObject arguments = choice("item", "Pressure Flake", round == 1 ? 9 : 8);
			JsonObject recipeData = new JsonObject();
			recipeData.addProperty("duration_ticks", round == 0 ? 0 : 200);
			arguments.add("recipeData", recipeData);
			return new OpenAiClient.ToolRound(
					List.of(new OpenAiClient.ToolCall("choice-" + round, "choose_output", arguments)),
					new JsonArray());
		};

		RecipeGenerationAgent.Choice repaired = RecipeGenerationAgent.choose(
				"test-model", runner, context, "Use pressure for 200 ticks.", schema);

		assertEquals(3, rounds.get());
		assertEquals(8, advertisedMaximum.get());
		assertEquals(8, repaired.outputCount());
		assertEquals(200, repaired.recipeData().get("duration_ticks").getAsInt());
		assertTrue(receivedHistories.get(1).toString().contains("invalid workstation recipeData"));
		assertTrue(receivedHistories.get(2).toString().contains("primary output slot with capacity 8"));
		assertTrue(prompt.get().contains("descriptive prompt material and is excluded from cache identity"));
		assertTrue(prompt.get().contains("canonical cacheDiscriminator"));
	}

	@Test
	void workstationChoiceValidationRejectsSchemaAndPrimarySlotOverflow() {
		DynamicWorkstationRecipeDataSchema schema = new DynamicWorkstationRecipeDataSchema(
				JsonParser.parseString("""
						{"type":"object","properties":{"duration_ticks":{"type":"integer","minimum":1}},
						 "required":["duration_ticks"],"additionalProperties":false}
						""").getAsJsonObject());
		JsonObject badData = choice("item", "Bad Flake", 1);
		badData.add("recipeData", new JsonObject());
		JsonObject overflow = choice("item", "Too Many Flakes", 9);
		JsonObject validData = new JsonObject();
		validData.addProperty("duration_ticks", 20);
		overflow.add("recipeData", validData);

		assertThrows(IOException.class,
				() -> RecipeGenerationAgent.validateChoice(badData, "test-model", schema, 8));
		assertThrows(IOException.class,
				() -> RecipeGenerationAgent.validateChoice(overflow, "test-model", schema, 8));
	}

	private static JsonObject choice(String kind, String name, Number count) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("kind", kind);
		arguments.addProperty("name", name);
		arguments.addProperty("count", count);
		return arguments;
	}
}
