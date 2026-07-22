package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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
	void generationWorkspaceCreatesWritableSoundSourceRoots() throws Exception {
		Path world = temporaryDirectory.resolve("sound-world");
		Path job = temporaryDirectory.resolve("sound-job");

		try (GenerationWorkspace ignored = GenerationWorkspace.testing(world, job)) {
			assertTrue(Files.isDirectory(world.resolve("sounds")));
			assertTrue(Files.isDirectory(job.resolve("sounds")));
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

		assertEquals(Set.of("bash", "read", "view_image", "grep", "glob", "write", "edit", "patch", "verify"), names);
		assertFalse(names.stream().anyMatch(name -> name.startsWith("set_")));
		assertFalse(names.contains("submit"));
		assertTrue(definitions.asList().stream().map(element -> element.getAsJsonObject())
				.filter(tool -> tool.get("name").getAsString().equals("verify"))
				.findFirst().orElseThrow().get("description").getAsString().contains("Compile"));
	}

	@Test
	void imageToolResultPreservesVisionInputAndExocortexImageBlock() {
		JsonObject metadata = new JsonObject();
		metadata.addProperty("ok", true);
		GeneralistGenerationTools.ToolResult result = new GeneralistGenerationTools.ToolResult(
				metadata, null, "data:image/png;base64,AQID");

		JsonArray response = result.responseOutput().getAsJsonArray();
		assertEquals("input_text", response.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("input_image", response.get(1).getAsJsonObject().get("type").getAsString());
		assertTrue(response.get(1).getAsJsonObject().get("image_url").getAsString().endsWith("AQID"));

		JsonArray exocortex = result.exocortexContent().getAsJsonArray();
		assertEquals("image", exocortex.get(1).getAsJsonObject().get("type").getAsString());
		assertEquals("AQID", exocortex.get(1).getAsJsonObject().getAsJsonObject("source")
				.get("data").getAsString());
	}

	@Test
	void viewImageRejectsOversizedPngHeaderBeforeRasterDecode() throws Exception {
		Path world = temporaryDirectory.resolve("image-world");
		Path job = temporaryDirectory.resolve("image-job");
		byte[] header = ByteBuffer.allocate(24)
				.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
				.putInt(13).put(new byte[] {'I', 'H', 'D', 'R'})
				.putInt(100_000).putInt(100_000).array();

		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.write(job.resolve("oversized.png"), header);
			GeneralistGenerationTools tools = new GeneralistGenerationTools(workspace,
					GenerationRequest.fixed(DynamicContentType.ITEM, null, "Image Test", 1, null));
			JsonObject arguments = new JsonObject();
			arguments.addProperty("path", "oversized.png");

			JsonObject result = tools.execute("view_image", arguments).output();

			assertEquals(false, result.get("ok").getAsBoolean());
			assertTrue(result.get("message").getAsString().contains("512x512"));
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
}
