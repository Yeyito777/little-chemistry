package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralistGenerationToolsTest {
	@TempDir
	Path temporaryDirectory;

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
					GenerationRequest.fixed(com.yeyito.littlechemistry.content.DynamicContentType.ITEM,
							null, "Image Test", 1, null));
			JsonObject arguments = new JsonObject();
			arguments.addProperty("path", "oversized.png");

			JsonObject result = tools.execute("view_image", arguments).output();

			assertEquals(false, result.get("ok").getAsBoolean());
			assertTrue(result.get("message").getAsString().contains("512x512"));
		}
	}
}
