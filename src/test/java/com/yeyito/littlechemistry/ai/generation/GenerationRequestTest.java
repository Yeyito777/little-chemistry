package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenerationRequestTest {
	@Test
	void fixedItemUsesAConciseNaturalQueryAndSelectsOneFocusedCapability() {
		String prompt = GenerationRequest.fixed(DynamicContentType.ITEM, null, "Moonlit Satchel", 1, null)
				.userPrompt();

		assertTrue(prompt.startsWith("Please generate and code a Minecraft item named \"Moonlit Satchel\""));
		assertTrue(prompt.contains("items/moonlit_satchel/C_moonlit_satchel_Content.java"));
		assertTrue(prompt.contains("package `items.c_moonlit_satchel`"));
		assertTrue(prompt.contains("{\"kind\":\"item\",\"capability\":\"ordinary|storage|projectile_weapon\"}"));
		assertTrue(prompt.contains("returns only that focused contract"));
		assertTrue(prompt.contains("reference/vanilla/TEXTURES.txt"));
		assertTrue(prompt.contains("indexed RRGGBBAA palette/rows representation"));
		assertTrue(prompt.contains("read_texture"));
		assertTrue(prompt.contains("never raster images"));
		assertFalse(prompt.contains("DynamicBlockUv"));
		assertFalse(prompt.contains("DynamicWorkstationParticles"));
		assertFalse(prompt.contains("context.firework"));
		assertFalse(prompt.contains("AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
	}

	@Test
	void fixedArmorReceivesOnlyItsAlreadyKnownFocusedContract() {
		String prompt = GenerationRequest.fixed(
				DynamicContentType.ARMOR, DynamicArmorSlot.HEAD, "Moonlit Crown", 1, null).userPrompt();

		assertTrue(prompt.contains("already selected focused implementation contract follows"));
		assertTrue(prompt.contains("x=40..47,y=0..7"));
		assertTrue(prompt.contains("DynamicArmorGeometryPart"));
		assertTrue(prompt.contains("reference/contracts/particles.md"));
		assertFalse(prompt.contains("DynamicWorkstationParticles"));
		assertFalse(prompt.contains("context.firework"));
		assertFalse(prompt.contains("inspect_generated_textures"));
		assertFalse(prompt.contains("view_image"));
	}

	@Test
	void ordinaryRecipeQuerySelectsFirstWithoutAdvertisingEveryCapability() {
		var recipe = JsonParser.parseString("""
				{"recipeType":"crafting","width":3,"height":3,
				 "grid":[{"itemId":"minecraft:leather"},{"itemId":"minecraft:string"}]}
				""").getAsJsonObject();
		String prompt = GenerationRequest.recipe(recipe, null, null).userPrompt();
		String compactPrompt = prompt.replaceAll("\\s+", " ");

		assertTrue(prompt.startsWith("Please generate and code the most natural piece of Minecraft content"));
		assertTrue(prompt.contains("item, block, armor piece, entity, or workstation"));
		assertTrue(prompt.contains("\"minecraft:leather\""));
		assertTrue(prompt.contains("`.littlechemistry/result.json`"));
		assertTrue(prompt.contains("\"kind\":\"item|block|workstation|helmet|chestplate|leggings|boots|entity\""));
		assertTrue(prompt.contains("\"capability\":\"ordinary|storage|projectile_weapon|workstation|armor|entity\""));
		assertTrue(prompt.contains("\"outputCount\":<natural integer>"));
		assertTrue(prompt.contains("\"ingredientUses\":{\"<slot>\":\"consume|keep|damage\"}"));
		assertTrue(prompt.contains("reusable utility implement"));
		assertTrue(prompt.contains("object being transformed are consumed whole"));
		assertTrue(prompt.contains("genuine unchanged catalyst, mold, or template"));
		assertTrue(prompt.contains("choose the natural output count from 1 to 64"));
		assertTrue(prompt.contains("Armor output count is always 1"));
		assertTrue(prompt.contains("`displayName` is the player-facing title"));
		assertTrue(prompt.contains("never a lowercase underscore identifier"));
		assertTrue(compactPrompt.contains("entity for an independently placed world object, creature, vehicle, or mount"));
		assertTrue(compactPrompt.contains("Select first; the tool that observes `.littlechemistry/result.json` returns only"));
		assertTrue(prompt.contains("automatically supplied recipe-item references") ||
				prompt.contains("separate recipe-item texture section"));
		assertFalse(prompt.contains("DynamicWorkstationSpec"));
		assertFalse(prompt.contains("DynamicWorkstationParticles"));
		assertFalse(prompt.contains("WorkstationTickBehavior"));
		assertFalse(prompt.contains("heldType `BOW`"));
		assertFalse(prompt.contains("context.firework"));
		assertFalse(prompt.contains("DynamicArmorGeometry"));
		assertFalse(prompt.contains("DynamicBlockUv"));
		assertFalse(prompt.contains("storage(new DynamicStorageSpec"));
		assertFalse(prompt.contains("\"kind\":\"rejection\""));
		assertFalse(prompt.contains("Open AGENTS.md"));
		assertFalse(prompt.contains("request.json"));
		assertTrue(prompt.length() < 4_500, "Initial prompt regressed to " + prompt.length() + " characters");
	}

	@Test
	void fixedFurnaceBlockSelectsBeforeReceivingItsContract() {
		GenerationRequest request = GenerationRequest.fixed(
				DynamicContentType.BLOCK, null, "Twin Furnace", 1, null);
		String prompt = request.userPrompt();
		var workspaceMetadata = GenerationWorkspace.encodeRequest(request);

		assertTrue(prompt.contains("{\"kind\":\"block\",\"capability\":\"ordinary|storage\"}"));
		assertTrue(prompt.contains("{\"kind\":\"workstation\",\"capability\":\"workstation\"}"));
		assertTrue(prompt.contains("functional machine or crafting/processing bench"));
		assertTrue(prompt.contains("observing tool returns only that focused contract"));
		assertEquals("block, workstation", workspaceMetadata.get("allowedKinds").getAsString());
		assertFalse(prompt.contains("DynamicWorkstationParticles"));
		assertFalse(prompt.contains("WorkstationTickBehavior"));
		assertFalse(prompt.contains("AI Workstation"));
	}

	@Test
	void focusedDocumentsRetainCompleteCapabilityGuidanceWithoutBloatedCommonIndex() {
		String index = GenerationContracts.API_INDEX;
		String allContracts = GenerationContracts.documents().stream()
				.map(GenerationContracts.Contract::content).reduce("", String::concat);

		assertTrue(index.length() < 3_000, "Common API index regressed to " + index.length() + " characters");
		assertFalse(index.contains("DynamicWorkstationParticles"));
		assertFalse(index.contains("DynamicArmorGeometryPart"));
		assertFalse(index.contains("context.firework"));
		assertFalse(index.contains("DynamicBlockUv"));
		assertTrue(contract("reference/contracts/armor.md").contains("x=40..47,y=0..7"));
		assertTrue(contract("reference/contracts/armor.md").contains("DynamicArmorGeometryPart"));
		assertTrue(contract("reference/contracts/entity.md").contains("DynamicBlockUv"));
		assertTrue(contract("reference/contracts/workstation.md").contains("DynamicWorkstationParticles"));
		assertTrue(contract("reference/contracts/workstation.md").contains("AI Workstation"));
		assertTrue(contract("reference/contracts/projectile-weapon.md").contains("context.firework"));
		assertTrue(contract("reference/contracts/storage.md").contains("new DynamicStorageSpec(rows, true)"));
		assertTrue(contract("reference/contracts/particles.md").contains("GeneratedContentBuilder.particles"));
		assertTrue(contract("reference/contracts/particles.md").contains("DynamicParticles.spawn"));
		assertTrue(contract("reference/contracts/particles.md").contains("Prefer an appropriate\nvanilla particle"));
		assertTrue(index.contains("`particles.md` is an optional cross-cutting contract"));
		assertTrue(contract("reference/contracts/workstation.md").contains("aiContext"));
		assertTrue(contract("reference/contracts/workstation.md").contains("cacheDiscriminator"));
		assertTrue(contract("reference/contracts/workstation.md").contains("visualReferenceDigest"));
		assertFalse(contract("reference/contracts/item.md").contains("cacheDiscriminator"));
		assertFalse(contract("reference/contracts/armor.md").contains("cacheDiscriminator"));
		assertFalse(allContracts.contains("Backpacks, satchels, bags"));
	}

	@Test
	void workstationQueryPrefixesUserLevelPolicyWithoutChangingSystemInstructions() {
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
		assertFalse(prompt.toLowerCase().contains("untrusted"));
		assertFalse(prompt.contains("instruction source"));
		assertFalse(ContentGenerationAgent.SYSTEM_PROMPT.contains(policy));
		assertFalse(workspaceMetadata.has("workstationPolicy"));
		assertTrue(workspaceMetadata.has("recipeDataSchema"));
		assertTrue(workspaceMetadata.get("allowedKinds").getAsString().contains("workstation"));
	}

	@Test
	void systemPromptStaysUniversalConciseAndStagesFocusedContracts() {
		String system = ContentGenerationAgent.SYSTEM_PROMPT;

		assertTrue(system.length() < 1_500);
		assertFalse(system.toLowerCase().contains("untrusted"));
		assertFalse(system.contains("instruction source"));
		assertTrue(system.contains("selected focused contract"));
		assertFalse(system.contains("AGENTS.md"));
		assertTrue(system.contains("reference/API.md"));
		assertFalse(system.contains("GeneratedBehaviorImpl.java"));
		assertFalse(system.toLowerCase().contains("rejection"));
		assertFalse(system.contains("workstation_too_weak"));
		assertTrue(system.contains("RRGGBBAA palette plus hexadecimal rows"));
		assertFalse(system.contains("DynamicWorkstationSpec"));
		assertFalse(system.contains("DynamicArmorGeometry"));
		assertFalse(system.contains("context.firework"));
		assertFalse(system.contains("view_image"));
		assertFalse(system.contains("input_image"));
		assertFalse(system.contains("inspect_generated_textures"));
		assertFalse(system.contains("ingredientUses"));
		assertFalse(system.contains("DynamicParticleDefinition"));
		assertFalse(system.contains("particles.md"));
	}

	@Test
	void ingredientUseChoiceIsScopedToOrdinaryCraftingQueries() {
		var smelting = JsonParser.parseString("""
				{"recipeType":"smelting","ingredient":{"itemId":"minecraft:iron_ore"}}
				""").getAsJsonObject();

		String prompt = GenerationRequest.recipe(smelting, null, null).userPrompt();

		assertFalse(prompt.contains("ingredientUses"));
		assertFalse(prompt.contains("reusable utility implement"));
	}

	@Test
	void automaticIngredientTexturesRemainASeparateUserMessageSection() {
		var recipe = JsonParser.parseString("""
				{"recipeType":"crafting","grid":[{"itemId":"minecraft:leather_helmet"}]}
				""").getAsJsonObject();
		RecipeVisualReferences.Bundle references = RecipeVisualReferences.forRequest(recipe);

		String prompt = GenerationRequest.recipe(recipe, null, null).userPrompt(references.promptSection());

		assertTrue(prompt.contains("## Automatically supplied recipe-item texture references"));
		assertTrue(prompt.contains("minecraft:item/leather_helmet"));
		assertTrue(prompt.contains("minecraft:entity/equipment/humanoid/leather"));
		assertTrue(prompt.contains("reference/vanilla/TEXTURES.txt"));
		assertFalse(prompt.toLowerCase().contains("untrusted"));
		assertFalse(prompt.contains("instruction source"));
		assertFalse(prompt.contains("image/png"));
		assertFalse(prompt.contains("inspect_generated_textures"));
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

	private static String contract(String path) {
		return GenerationContracts.documents().stream()
				.filter(contract -> contract.path().equals(path))
				.findFirst().orElseThrow().content();
	}

	private static int occurrences(String text, String target) {
		int count = 0;
		for (int index = text.indexOf(target); index >= 0; index = text.indexOf(target, index + target.length())) {
			count++;
		}
		return count;
	}
}
