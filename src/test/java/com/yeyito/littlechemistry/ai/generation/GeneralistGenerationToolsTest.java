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
					DynamicContentType.ENTITY, null, "Sandbox Entity", 1, null);
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

		assertEquals(Set.of("bash", "read", "read_texture", "read_entity_reference",
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

	@Test
	void focusedContractsAreMaterializedInEveryGenerationJob() throws Exception {
		Path world = temporaryDirectory.resolve("contract-reference-world");
		for (int job = 0; job < 2; job++) {
			try (GenerationWorkspace workspace = GenerationWorkspace.open(world,
					GenerationRequest.recipe(new JsonObject(), null, null))) {
				assertTrue(Files.readString(workspace.root().resolve("reference/API.md"))
						.contains("Choose the result before studying implementation details"));
				for (GenerationContracts.Contract contract : GenerationContracts.documents()) {
					Path materialized = workspace.root().resolve(contract.path());
					assertTrue(Files.isRegularFile(materialized), contract.path());
					assertEquals(contract.content(), Files.readString(materialized));
				}
			}
		}
	}

	@Test
	void sourceMutationIsRejectedUntilTheFocusedContractIsDelivered() throws Exception {
		Path world = temporaryDirectory.resolve("staged-world");
		Path job = temporaryDirectory.resolve("staged-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), null, null));
			JsonObject prematureWrite = new JsonObject();
			prematureWrite.addProperty("path", "items/premature/source.txt");
			prematureWrite.addProperty("content", "too early");
			JsonObject rejectedWrite = tools.execute("write", prematureWrite).output();
			assertFalse(rejectedWrite.get("ok").getAsBoolean());
			assertTrue(rejectedWrite.get("message").getAsString().contains("receive its focused contract"));

			JsonObject bundledPatch = new JsonObject();
			bundledPatch.addProperty("diff", """
					--- /dev/null
					+++ .littlechemistry/result.json
					@@ -0,0 +1 @@
					+{"kind":"item","capability":"ordinary","displayName":"Staged Item","outputCount":1}
					--- /dev/null
					+++ items/premature/source.txt
					@@ -0,0 +1 @@
					+too early
					""");
			JsonObject rejectedPatch = tools.execute("patch", bundledPatch).output();
			assertFalse(rejectedPatch.get("ok").getAsBoolean());
			assertFalse(Files.exists(job.resolve(".littlechemistry/result.json")));
			assertFalse(Files.exists(job.resolve("items/premature/source.txt")));
		}
	}

	@Test
	void incompleteSelectionDoesNotUnlockSourceMutation() throws Exception {
		Path world = temporaryDirectory.resolve("incomplete-selection-world");
		Path job = temporaryDirectory.resolve("incomplete-selection-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), null, null));
			JsonObject incomplete = new JsonObject();
			incomplete.addProperty("path", ".littlechemistry/result.json");
			incomplete.addProperty("content", "{\"kind\":\"item\",\"capability\":\"ordinary\"}");

			JsonObject result = tools.execute("write", incomplete).output();

			assertFalse(result.has("selectedContract"));
			assertTrue(result.get("selectionContractError").getAsString().contains("displayName"));
			JsonObject source = new JsonObject();
			source.addProperty("path", "items/premature/source.txt");
			source.addProperty("content", "still too early");
			assertFalse(tools.execute("write", source).output().get("ok").getAsBoolean());

			incomplete.addProperty("content", "{\"kind\":\"item\",\"capability\":\"ordinary\","
					+ "\"displayName\":\"Complete Item\",\"outputCount\":1}");
			assertEquals("reference/contracts/item.md",
					tools.execute("write", incomplete).output().get("selectedContractPath").getAsString());
		}
	}

	@Test
	void bashCannotBundleFirstSelectionWithSourceAuthoring() throws Exception {
		Path world = temporaryDirectory.resolve("staged-bash-world");
		Path job = temporaryDirectory.resolve("staged-bash-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), null, null));
			JsonObject arguments = new JsonObject();
			arguments.addProperty("command", "printf '%s' '{\"kind\":\"item\",\"capability\":\"ordinary\","
					+ "\"displayName\":\"Staged Item\",\"outputCount\":1}' > .littlechemistry/result.json; "
					+ "printf staged-source > .littlechemistry/Early.java; printf too-early > items/premature.txt");

			JsonObject selected = tools.execute("bash", arguments).output();

			assertTrue(selected.has("selectedContract"), selected.toString());
			assertEquals("reference/contracts/item.md", selected.get("selectedContractPath").getAsString());
			assertFalse(Files.exists(job.resolve(".littlechemistry/Early.java")));
			assertFalse(Files.exists(job.resolve("items/premature.txt")));
			JsonObject afterContract = new JsonObject();
			afterContract.addProperty("command", "mkdir -p items/staged && printf after-contract > items/staged/source.txt");
			JsonObject authored = tools.execute("bash", afterContract).output();
			assertEquals(0, authored.get("exitCode").getAsInt(), authored.toString());
			assertEquals("after-contract", Files.readString(job.resolve("items/staged/source.txt")));
		}
	}

	@Test
	void selectedItemCapabilityDeliversOnlyItsExactSubtypeContract() throws Exception {
		for (var scenario : List.of(
				List.of("storage", "reference/contracts/storage.md", "DynamicStorageSpec", "context.firework"),
				List.of("projectile_weapon", "reference/contracts/projectile-weapon.md", "context.firework", "DynamicStorageSpec"))) {
			Path world = temporaryDirectory.resolve("subtype-world-" + scenario.get(0));
			Path job = temporaryDirectory.resolve("subtype-job-" + scenario.get(0));
			try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
				GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
						GenerationRequest.recipe(new JsonObject(), null, null));
				JsonObject selection = new JsonObject();
				selection.addProperty("path", ".littlechemistry/result.json");
				selection.addProperty("content", "{\"kind\":\"item\",\"capability\":\"" + scenario.get(0)
						+ "\",\"displayName\":\"Focused Item\",\"outputCount\":1}");

				JsonObject selected = tools.execute("write", selection).output();

				assertEquals(scenario.get(1), selected.get("selectedContractPath").getAsString());
				assertTrue(selected.get("selectedContract").getAsString().contains(scenario.get(2)));
				assertFalse(selected.get("selectedContract").getAsString().contains(scenario.get(3)));
				assertFalse(selected.get("selectedContract").getAsString().contains("DynamicWorkstationParticles"));
				assertFalse(selected.get("selectedContract").getAsString().contains("DynamicArmorGeometryPart"));
			}
		}
	}

	@Test
	void resultSelectionReturnsOnlyTheFocusedContractOnce() throws Exception {
		Path world = temporaryDirectory.resolve("contract-world");
		Path job = temporaryDirectory.resolve("contract-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), null, null));
			JsonObject selection = new JsonObject();
			selection.addProperty("path", ".littlechemistry/result.json");
			selection.addProperty("content", "{\"kind\":\"helmet\",\"capability\":\"armor\","
					+ "\"displayName\":\"Moonlit Crown\",\"outputCount\":1}");

			JsonObject selected = tools.execute("write", selection).output();

			assertEquals("reference/contracts/armor.md", selected.get("selectedContractPath").getAsString());
			assertTrue(selected.get("selectedContract").getAsString().contains("DynamicArmorGeometryPart"));
			assertFalse(selected.get("selectedContract").getAsString().contains("DynamicWorkstationParticles"));
			assertFalse(selected.get("selectedContract").getAsString().contains("context.firework"));
			JsonObject source = new JsonObject();
			source.addProperty("path", "armors/moonlit_crown/notes.txt");
			source.addProperty("content", "focused");
			JsonObject subsequent = tools.execute("write", source).output();
			assertFalse(subsequent.has("selectedContract"));
		}
	}

	@Test
	void rejectionContractIsTerminalAndPreventsSourceAuthoring() throws Exception {
		Path world = temporaryDirectory.resolve("terminal-rejection-world");
		Path job = temporaryDirectory.resolve("terminal-rejection-job");
		var schema = JsonParser.parseString(
				"{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}")
				.getAsJsonObject();
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.recipe(new JsonObject(), "Results are modest utilities.", schema));
			JsonObject selection = new JsonObject();
			selection.addProperty("path", ".littlechemistry/result.json");
			selection.addProperty("content", "{\"kind\":\"rejection\","
					+ "\"category\":\"workstation_too_weak\","
					+ "\"description\":\"This workstation is too weak to stabilize that transformation.\"}");

			JsonObject selected = tools.execute("write", selection).output();
			assertEquals("reference/contracts/rejection.md", selected.get("selectedContractPath").getAsString());
			JsonObject source = new JsonObject();
			source.addProperty("path", "items/forbidden/source.txt");
			source.addProperty("content", "must not exist");
			JsonObject rejected = tools.execute("write", source).output();
			assertFalse(rejected.get("ok").getAsBoolean());
			assertTrue(rejected.get("message").getAsString().contains("rejection is terminal"));
			assertFalse(Files.exists(job.resolve("items/forbidden/source.txt")));
			selection.addProperty("content", "{\"kind\":\"item\",\"capability\":\"ordinary\","
					+ "\"displayName\":\"Changed Mind\",\"outputCount\":1,\"recipeData\":{}}");
			JsonObject reclassified = tools.execute("write", selection).output();
			assertFalse(reclassified.get("ok").getAsBoolean());
			assertTrue(reclassified.get("message").getAsString().contains("without changing the selection"));
			assertTrue(Files.readString(job.resolve(".littlechemistry/result.json")).contains("\"kind\":\"rejection\""));
		}
	}

	@Test
	void fixedKnownKindsIgnoreIncidentalResultFiles() throws Exception {
		Path world = temporaryDirectory.resolve("fixed-known-world");
		Path job = temporaryDirectory.resolve("fixed-known-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.fixed(DynamicContentType.ARMOR,
							com.yeyito.littlechemistry.content.DynamicArmorSlot.HEAD, "Known Crown", 1, null));
			JsonObject incidental = new JsonObject();
			incidental.addProperty("path", ".littlechemistry/result.json");
			incidental.addProperty("content", "{\"kind\":\"workstation\",\"capability\":\"workstation\"}");

			JsonObject written = tools.execute("write", incidental).output();

			assertFalse(written.has("selectedContract"));
			assertFalse(written.has("selectionContractError"));
			JsonObject source = new JsonObject();
			source.addProperty("path", "armors/known_crown/source.txt");
			source.addProperty("content", "allowed");
			assertTrue(tools.execute("write", source).output().get("ok").getAsBoolean());
		}
	}

	@Test
	void fixedBlockSelectionReturnsWorkstationContractAfterClassification() throws Exception {
		Path world = temporaryDirectory.resolve("block-contract-world");
		Path job = temporaryDirectory.resolve("block-contract-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.fixed(DynamicContentType.BLOCK, null, "Twin Furnace", 1, null));
			JsonObject selection = new JsonObject();
			selection.addProperty("path", ".littlechemistry/result.json");
			selection.addProperty("content", "{\"kind\":\"workstation\",\"capability\":\"workstation\"}");

			JsonObject selected = tools.execute("write", selection).output();

			assertEquals("reference/contracts/workstation.md", selected.get("selectedContractPath").getAsString());
			assertTrue(selected.get("selectedContract").getAsString().contains("DynamicWorkstationParticles"));
			assertFalse(selected.get("selectedContract").getAsString().contains("DynamicArmorGeometryPart"));
		}
	}

	private static List<String> strings(JsonObject object, String name) {
		return object.getAsJsonArray(name).asList().stream().map(element -> element.getAsString()).toList();
	}
}
