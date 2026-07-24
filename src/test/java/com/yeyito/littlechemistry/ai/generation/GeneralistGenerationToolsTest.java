package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneralistGenerationToolsTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void bashRunsInAWorldJobSandboxAndCannotRewriteEngineInputs() throws Exception {
		Path world = temporaryDirectory.resolve("world");
		Path job = temporaryDirectory.resolve("job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.createDirectories(job.resolve("existing"));
			Files.createDirectories(job.resolve("reference"));
			Files.createDirectories(job.resolve(".existing-sourcepath"));
			Files.writeString(job.resolve("request.json"), "immutable");
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Sandbox Item", 1, null);
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace, request);
			JsonObject arguments = new JsonObject();
			arguments.addProperty("command",
					"printf ok > items/created.txt; ln -s /etc/passwd items/host-link; printf changed > request.json");

				var result = tools.execute("bash", arguments);
				JsonObject readLink = new JsonObject();
				readLink.addProperty("path", "items/host-link");
				var escapedRead = tools.execute("read", readLink);
				JsonObject grepItems = new JsonObject();
				grepItems.addProperty("pattern", "root:");
				grepItems.addProperty("path", "items");
				var escapedGrep = tools.execute("grep", grepItems);

			assertEquals(1, result.output().get("exitCode").getAsInt());
			assertEquals("ok", Files.readString(job.resolve("items/created.txt")));
			assertEquals("immutable", Files.readString(job.resolve("request.json")));
				assertFalse(escapedRead.output().get("ok").getAsBoolean());
				assertEquals(0, escapedGrep.output().get("matches").getAsInt());
			}
		}

	@Test
	void javaIdentityIsSafeForDigitLeadingAndKeywordRuntimeIds() {
		for (String identifier : Set.of("2_solution", "class", "record", "ordinary_name")) {
			assertTrue(GenerationWorkspace.javaPackageSegment(identifier).matches("[A-Za-z_$][A-Za-z0-9_$]*"));
			assertTrue(GenerationWorkspace.className(identifier).matches("[A-Za-z_$][A-Za-z0-9_$]*"));
		}
	}

	@Test
	void exposesOnlyGeneralCodingToolsAndTheFinalVerifier() {
		JsonArray definitions = GeneralistGenerationTools.definitions();
		Set<String> names = new HashSet<>();
		definitions.forEach(element -> names.add(element.getAsJsonObject().get("name").getAsString()));

		assertEquals(Set.of("bash", "read", "read_texture",
				"grep", "glob", "write", "edit", "patch", "verify"), names);
		assertFalse(names.contains("inspect_generated_textures"));
		assertFalse(names.contains("view_image"));
		assertFalse(names.contains("preview_armor"));
		assertFalse(names.stream().anyMatch(name -> name.startsWith("set_")));
		assertFalse(names.contains("submit"));
		assertTrue(definitions.asList().stream().map(element -> element.getAsJsonObject())
				.filter(tool -> tool.get("name").getAsString().equals("verify"))
				.findFirst().orElseThrow().get("description").getAsString().contains("Compile"));
	}

	@Test
	void toolResultsReachResponsesAndExocortexAsTextOnly() {
		JsonObject output = new JsonObject();
		output.addProperty("ok", true);
		output.addProperty("representation", "palette and rows");
		var result = new GeneralistGenerationTools.ToolResult(output, null);

		assertTrue(result.responseOutput().isJsonPrimitive());
		assertTrue(result.exocortexContent().isJsonPrimitive());
		assertEquals(output.toString(), result.responseOutput().getAsString());
		assertEquals(output.toString(), result.exocortexContent().getAsString());
		assertFalse(result.responseOutput().toString().contains("input_image"));
		assertFalse(result.exocortexContent().toString().contains("image/png"));
	}

	@Test
	void recipeItemReferencesAreAutomaticallySuppliedAsExactTextIncludingEquipmentLayers() {
		JsonObject recipe = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:leather_helmet"},
				         {"itemId":"minecraft:golden_chestplate"}]}
				""").getAsJsonObject();

		RecipeVisualReferences.Bundle references = RecipeVisualReferences.forRequest(recipe);

		assertTrue(references.promptSection().contains("minecraft:item/leather_helmet"));
		assertTrue(references.promptSection().contains("minecraft:item/leather_helmet_overlay"));
		assertTrue(references.promptSection().contains("minecraft:entity/equipment/humanoid/leather"));
		assertTrue(references.promptSection().contains("minecraft:item/golden_chestplate"));
		assertTrue(references.promptSection().contains("minecraft:entity/equipment/humanoid/gold"));
		assertTrue(references.promptSection().contains("RRGGBBAA"));
		assertTrue(references.promptSection().contains("\"palette\""));
		assertTrue(references.promptSection().contains("\"rows\""));
		assertFalse(references.promptSection().contains("image/png"));
		assertFalse(references.promptSection().contains("input_image"));
		assertTrue(references.digest().matches("[a-f0-9]{64}"));
	}

	@Test
	void textureToolsRejectAliasesTraversalAndNonArmorReferenceReceipts() throws Exception {
		Path world = temporaryDirectory.resolve("canonical-world");
		Path job = temporaryDirectory.resolve("canonical-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.fixed(DynamicContentType.ITEM, null, "Canonical Item", 1, null));

			for (String path : List.of(
					"reference/./vanilla/item/diamond_chestplate.json",
					"reference/vanilla//item/diamond_chestplate.json",
					"reference/vanilla/../../request.json",
					job.resolve("reference/vanilla/item/diamond_chestplate.json").toString())) {
				JsonObject arguments = new JsonObject();
				arguments.addProperty("path", path);
				var result = tools.execute("read_texture", arguments);
				assertFalse(result.output().get("ok").getAsBoolean(), path + ": " + result.output());
			}
			JsonObject aliasedRead = new JsonObject();
			aliasedRead.addProperty("path", "reference/./vanilla/item/diamond_chestplate.json");
			var decorated = tools.execute("read", aliasedRead);
			assertFalse(decorated.output().get("ok").getAsBoolean());
			assertTrue(decorated.output().get("message").getAsString().contains("read_texture"));

			JsonObject elytra = new JsonObject();
			elytra.addProperty("path", "reference/vanilla/item/elytra.json");
			JsonObject humanoid = new JsonObject();
			humanoid.addProperty("path", "reference/vanilla/entity/equipment/humanoid/diamond.json");
			assertTrue(tools.execute("read_texture", elytra).output().get("ok").getAsBoolean());
			assertTrue(tools.execute("read_texture", humanoid).output().get("ok").getAsBoolean());
			JsonObject leggings = new JsonObject();
			leggings.addProperty("path", "reference/vanilla/item/diamond_leggings.json");
			assertTrue(tools.execute("read_texture", leggings).output().get("ok").getAsBoolean());
			JsonObject leggingsLayer = new JsonObject();
			leggingsLayer.addProperty("path", "reference/vanilla/entity/equipment/humanoid_leggings/diamond.json");
			assertTrue(tools.execute("read_texture", leggingsLayer).output().get("ok").getAsBoolean());
		}
	}

	@Test
	void largeVanillaUvSheetsRemainAvailableAsTextualIndexedTiles() throws Exception {
		Path world = temporaryDirectory.resolve("large-reference-world");
		Path job = temporaryDirectory.resolve("large-reference-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.fixed(DynamicContentType.ITEM, null, "Boat Study", 1, null));
			JsonObject arguments = new JsonObject();
			arguments.addProperty("path", "reference/vanilla/entity/boat/oak.json");

			JsonObject output = tools.execute("read_texture", arguments).output();

			assertTrue(output.get("ok").getAsBoolean(), output.toString());
			assertTrue(output.has("tiles"), output.toString());
			assertEquals(2, output.getAsJsonArray("tiles").size());
			for (var tile : output.getAsJsonArray("tiles")) {
				assertTrue(tile.getAsJsonObject().has("palette"));
				assertTrue(tile.getAsJsonObject().has("rows"));
			}
			assertFalse(output.toString().contains("image/png"));
		}
	}

	@Test
	void verifyAcceptsACompleteWorkstationRejectionWithoutGeneratedSource() throws Exception {
		Path world = temporaryDirectory.resolve("rejection-world");
		Path job = temporaryDirectory.resolve("rejection-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.createDirectories(job.resolve(".littlechemistry"));
			Files.writeString(job.resolve(".littlechemistry/result.json"), """
					{"kind":"rejection","category":"workstation_too_weak",
					 "description":"This workstation is too weak to shape that spell safely."}
					""");
			JsonObject context = JsonParser.parseString("""
					{"workstation":{"primaryOutput":{"id":"result","capacity":64}}}
					""").getAsJsonObject();
			JsonObject schema = JsonParser.parseString("""
					{"type":"object","properties":{},"required":[],"additionalProperties":false}
					""").getAsJsonObject();
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(context, "Use the correct workstation.", schema));

			var result = tools.execute("verify", new JsonObject());

			assertTrue(result.output().get("ok").getAsBoolean());
			assertEquals("rejection", result.output().get("kind").getAsString());
			assertEquals(WorkstationRecipeRejection.Category.WORKSTATION_TOO_WEAK,
					result.rejection().category());
			assertTrue(result.verified() == null);
		}
	}

	@Test
	void ordinaryRecipeCannotUseAWorkstationRejection() throws Exception {
		Path world = temporaryDirectory.resolve("ordinary-world");
		Path job = temporaryDirectory.resolve("ordinary-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.createDirectories(job.resolve(".littlechemistry"));
			Files.writeString(job.resolve(".littlechemistry/result.json"), """
					{"kind":"rejection","category":"workstation_too_weak",
					 "description":"This workstation is too weak for the requested transformation."}
					""");
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), null, null));

			var result = tools.execute("verify", new JsonObject());

			assertFalse(result.output().get("ok").getAsBoolean());
			assertTrue(result.output().get("message").getAsString().contains("Only workstation recipes"));
		}
	}

	private static List<String> strings(JsonObject object, String name) {
		return object.getAsJsonArray(name).asList().stream().map(element -> element.getAsString()).toList();
	}
}
