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
		assertTrue(prompt.contains("`helpers/moonlit_satchel/`"));
		assertTrue(prompt.contains("existing vanilla or modded textures"));
		assertTrue(prompt.contains("palettes, pixel arrangements, silhouettes"));
		assertTrue(prompt.endsWith("Also consider whether the result warrants native Minecraft sound design, custom "
				+ "particles, runtime behavior helpers, or other reusable helpers, and create the appropriate source under "
				+ "sounds/, particles/, or helpers/."));
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
		assertFalse(prompt.contains("special workstation"));
		assertFalse(prompt.contains("workstation's output policy"));
	}

	@Test
	void workstationQueryPrefixesUserLevelPolicyWithIdentityWithoutChangingSystemInstructions() {
		var recipe = JsonParser.parseString("""
				{
				  "recipeType":"workstation",
				  "process":"little_chemistry:workstation/arcane_workbench/arcane_weaving",
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
		assertTrue(prompt.contains("native Minecraft sound design"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains(policy));
		assertFalse(workspaceMetadata.has("workstationPolicy"));
		assertTrue(workspaceMetadata.has("recipeDataSchema"));
	}

	@Test
	void systemPromptContainsStaticWorkspaceContractWithoutAgentsFileDirective() {
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.toLowerCase().contains("untrusted"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains("AGENTS.md"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("reference/API.md"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("GeneratedBehaviorImpl.java"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("inspect relevant indexed vanilla"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("Every request uses these same system instructions"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("declarative design data in the user request"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("deterministic input eligibility and consumption"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("sounds/<id>"));
		assertTrue(ContentGenerationAgent.SYSTEM_PROMPT.contains("helpers/<id>"));
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

	private static int occurrences(String text, String target) {
		return (text.length() - text.replace(target, "").length()) / target.length();
	}
}
