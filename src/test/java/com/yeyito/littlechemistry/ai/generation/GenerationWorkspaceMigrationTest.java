package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicRarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenerationWorkspaceMigrationTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void formatNineteenDescriptionDigestStillRecoversCommittedPendingSource() throws Exception {
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "legacy_glider", "Legacy Glider",
				"A membrane winged oak skiff that glides wherever its rider looks.", DynamicRarity.COMMON,
				0L, "0".repeat(64), null, null, null, null, DynamicItemProperties.DEFAULT, null,
				DynamicBehaviorSource.completeLegacySource(null), null);
		assertNotEquals(GenerationWorkspace.definitionDigest(definition),
				GenerationWorkspace.legacyDescriptionDefinitionDigest(definition));

		Path world = temporaryDirectory.resolve("generation-workspace");
		Path pending = world.resolve(".pending/recovery");
		Path source = pending.resolve("items/legacy_glider/OriginalFactory.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "// exact authored factory source\n");
		JsonObject manifest = new JsonObject();
		manifest.addProperty("type", "item");
		manifest.addProperty("identifier", "legacy_glider");
		manifest.addProperty("sourceDigest", GenerationWorkspace.sourceDigest(pending));
		manifest.addProperty("definitionDigest",
				GenerationWorkspace.legacyDescriptionDefinitionDigest(definition));
		Files.writeString(pending.resolve("manifest.json"), manifest.toString());

		GenerationWorkspace.initialize(world, List.of(definition));

		assertTrue(Files.isRegularFile(world.resolve("items/legacy_glider/OriginalFactory.java")));
		assertFalse(Files.exists(pending));
	}
}
