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
		assertTrue(prompt.contains("Always base the visual design on existing vanilla or modded textures"));
		assertFalse(prompt.contains("Open AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
	}

	@Test
	void systemPromptContainsNoUntrustedOrAgentsFileDirective() {
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.toLowerCase().contains("untrusted"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains("AGENTS.md"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("inspect relevant vanilla"));
	}
}
