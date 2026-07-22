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
	void armorQueryExplicitlyRequiresHeadUvReferenceInspection() {
		String prompt = GenerationRequest.fixed(
				DynamicContentType.ARMOR, com.yeyito.littlechemistry.content.DynamicArmorSlot.HEAD,
				"Moonlit Crown", 1, null).userPrompt();

		assertTrue(prompt.contains("reference/vanilla/TEXTURES.txt"));
		assertTrue(prompt.contains("16x16 inventory icon"));
		assertTrue(prompt.contains("64x32 equipment sheet"));
		assertTrue(prompt.contains("base-head UV region at x=0..31, y=0..15"));
		assertTrue(prompt.contains("hat/outer-head region at x=32..63, y=0..15"));
		assertTrue(prompt.contains("renders on the player's head"));
	}

	@Test
	void ordinaryRecipeQueryInfersKindAndChoosesANaturalOutputCountWithoutRejectionPriming() {
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
		assertTrue(prompt.contains("\"outputCount\":<natural integer>"));
		assertTrue(prompt.contains("choose the natural output count from 1 to 64"));
		assertTrue(prompt.contains("Armor output count is always 1"));
		assertFalse(prompt.contains("\"outputCount\":1"));
		assertTrue(prompt.contains("Always base the visual design on existing vanilla or modded textures"));
		assertFalse(prompt.contains("\"kind\":\"rejection\""));
		assertFalse(prompt.contains("Open AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
		assertTrue(prompt.contains("select result kind `block`"));
		assertTrue(prompt.contains("non-null `DynamicWorkstationSpec`"));
		assertTrue(prompt.contains("Furnaces, powered processors, crafting benches, and workbenches"));
		assertTrue(prompt.contains("functional workstations rather than decorative blocks"));
		assertTrue(prompt.contains("`WorkstationBehavior` and `WorkstationTickBehavior`"));
		assertTrue(prompt.contains("heldType `BOW` or `CROSSBOW`"));
		assertTrue(prompt.contains("outputCount 1, and positive enchantability"));
		assertTrue(prompt.contains("do not reimplement those mechanics"));
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
		assertTrue(prompt.contains("\"outputCount\":<natural integer>"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains(policy));
		assertFalse(workspaceMetadata.has("workstationPolicy"));
		assertTrue(workspaceMetadata.has("recipeDataSchema"));
	}

	@Test
	void systemPromptStaysConciseWithoutWorkspaceInstructionFiles() {
		String system = ContentGenerationAgent.SYSTEM_PROMPT;

		assertTrue(system.length() < 2_000);
		assertTrue(system.toLowerCase().contains("untrusted"));
		assertFalse(system.contains("AGENTS.md"));
		assertTrue(system.contains("reference/API.md"));
		assertFalse(system.contains("GeneratedBehaviorImpl.java"));
		assertFalse(system.toLowerCase().contains("rejection"));
		assertFalse(system.contains("workstation_too_weak"));
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
	}

	private static int occurrences(String text, String target) {
		int count = 0;
		for (int index = text.indexOf(target); index >= 0; index = text.indexOf(target, index + target.length())) {
			count++;
		}
		return count;
	}
}
