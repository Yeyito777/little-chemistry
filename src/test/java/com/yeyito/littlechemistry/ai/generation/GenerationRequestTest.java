package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void workstationQueryPrefixesUserLevelPolicyWithIdentityWithoutChangingSystemInstructions() {
		var recipe = JsonParser.parseString("""
				{
				  "recipeType":"workstation",
				  "workstation":{
				    "contentId":"little_chemistry:arcane_workbench",
				    "displayName":"Arcane Workbench",
				    "description":"A lapis-inlaid workbench for restrained arcane creations.",
				    "processDescription":"Weaves one result over 40 Minecraft ticks.",
				    "primaryOutput":{"id":"result","capacity":64}
				  },
				  "ingredients":[{"slot":"input","itemId":"minecraft:paper"}]
				}
				""").getAsJsonObject();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{},"additionalProperties":false}
				""").getAsJsonObject();
		String policy = "Results are restrained arcane utilities grounded in their ingredients.";
		GenerationRequest request = GenerationRequest.recipe(recipe, policy, schema);

		String prompt = request.userPrompt();
		var workspaceMetadata = GenerationWorkspace.encodeRequest(request);

		assertTrue(prompt.contains("This recipe is being created in the following special workstation:\n"
				+ "Name: Arcane Workbench\n"
				+ "Description: A lapis-inlaid workbench for restrained arcane creations.\n\n"
				+ "The workstation's output policy is:\n" + policy));
		assertEquals(1, occurrences(prompt, "Arcane Workbench"));
		assertEquals(1, occurrences(prompt, policy));
		assertTrue(prompt.contains("The required recipe-data schema is:"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains(policy));
		assertFalse(workspaceMetadata.has("workstationPolicy"));
		assertTrue(workspaceMetadata.has("recipeDataSchema"));
	}

	@Test
	void systemPromptStaysConciseAndLeavesDetailedContractsInWorkspaceDocumentation() {
		String system = ContentGenerationAgent.SYSTEM_PROMPT;
		String documentation = GenerationWorkspace.agents(
				GenerationRequest.fixed(DynamicContentType.ITEM, null, "Moonlit Satchel", 1, null));

		assertTrue(system.length() < 2_000);
		assertTrue(system.toLowerCase().contains("untrusted"));
		assertTrue(system.contains("AGENTS.md"));
		assertTrue(system.contains("reference/API.md"));
		assertFalse(system.contains("GeneratedBehaviorImpl.java"));
		assertFalse(system.toLowerCase().contains("rejection"));
		assertFalse(system.contains("workstation_too_weak"));

		assertTrue(documentation.contains("## Source organization"));
		assertTrue(documentation.contains("GeneratedBehaviorImpl.java"));
		assertTrue(documentation.contains("reference/classes/INDEX.txt"));
		assertTrue(documentation.contains("reference/API.md"));
		assertTrue(documentation.contains("recipePolicy"));
		assertFalse(documentation.toLowerCase().contains("rejection"));
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
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.toLowerCase().contains("rejection"));
		assertFalse(GenerationWorkspace.agents(
				GenerationRequest.recipe(recipe, "Shape an appropriate result.", schema))
				.toLowerCase().contains("rejection"));
	}

	private static int occurrences(String text, String target) {
		int count = 0;
		for (int index = text.indexOf(target); index >= 0; index = text.indexOf(target, index + target.length())) {
			count++;
		}
		return count;
	}
}
