package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenerationRequestTest {
	@Test
	void fixedItemUsesANaturalContentQueryWithMandatoryTextureStudy() {
		String prompt = GenerationRequest.fixed(DynamicContentType.ITEM, null, "Moonlit Satchel", 1, null)
				.userPrompt();

		assertTrue(prompt.startsWith("Please generate and code a Minecraft item named \"Moonlit Satchel\""));
		assertTrue(prompt.contains("items/moonlit_satchel/C_moonlit_satchel_Content.java"));
		assertTrue(prompt.contains("package `items.c_moonlit_satchel`"));
		assertTrue(prompt.contains("existing vanilla or modded textures"));
		assertTrue(prompt.contains("palettes, pixel arrangements, silhouettes"));
		assertFalse(prompt.contains("AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
	}

	@Test
	void recipeQueryPresentsTheRecipeDirectlyAndLetsTheModelInferTheContentKind() {
		var recipe = JsonParser.parseString("""
				{"recipeType":"crafting","width":3,"height":3,
				 "grid":[{"itemId":"minecraft:leather"},{"itemId":"minecraft:string"}]}
				""").getAsJsonObject();
		String prompt = GenerationRequest.recipe(recipe, null, null).userPrompt();

		assertTrue(prompt.startsWith("Please generate and code the most natural piece of Minecraft content"));
		assertTrue(prompt.contains("item, block, armor piece, entity, or workstation"));
		assertTrue(prompt.contains("\"minecraft:leather\""));
		assertTrue(prompt.contains("`.littlechemistry/result.json`"));
		assertTrue(prompt.contains("\"kind\":\"item|block|helmet|chestplate|leggings|boots|entity\""));
		assertTrue(prompt.contains("Always base the visual design on existing vanilla or modded textures"));
		assertFalse(prompt.contains("\"kind\":\"rejection\""));
		assertFalse(prompt.contains("Open AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
	}

	@Test
	void systemPromptContainsStaticWorkspaceContractWithoutAgentsFileDirective() {
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.toLowerCase().contains("untrusted"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains("AGENTS.md"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("reference/API.md"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("GeneratedBehaviorImpl.java"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("inspect relevant indexed vanilla"));
	}

	@Test
	void workstationPromptOnlyPermitsRejectionWhenTheWorkstationIsTooWeak() {
		var recipe = JsonParser.parseString("""
				{"recipeType":"workstation","ingredients":[{"itemId":"minecraft:stone"}]}
				""").getAsJsonObject();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{},"required":[],"additionalProperties":false}
				""").getAsJsonObject();

		String prompt = GenerationRequest.recipe(recipe, "Shape an appropriate result.", schema).userPrompt();

		assertTrue(prompt.contains("workstation is too weak"));
		assertFalse(prompt.contains("recipe is nonsense"));
		assertFalse(prompt.contains("wrong workstation"));
		assertTrue(prompt.contains("one or two short, complete sentences"));
		assertTrue(prompt.contains("\"kind\":\"rejection\""));
		assertTrue(prompt.contains("\"category\":\"workstation_too_weak\""));
		assertTrue(prompt.contains("call `verify` immediately"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("only when the user request permits it"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("category workstation_too_weak"));
	}
}
