package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentJson;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicRarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

	@Test
	void identifierStyleDisplayNameDigestStillRecoversCommittedPendingSource() throws Exception {
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "phantom_skiff", "phantom_skiff",
				"A light oak vessel reinforced with phantom membrane.", DynamicRarity.COMMON,
				0L, "0".repeat(64), null, null, null, null, DynamicItemProperties.DEFAULT, null,
				DynamicBehaviorSource.completeLegacySource(null), null);
		assertTrue(GenerationWorkspace.matchesCompatibleDefinitionDigest(
				legacyDisplayNameDigest(definition, "phantom_skiff"), definition));

		Path world = temporaryDirectory.resolve("identifier-name-workspace");
		Path pending = world.resolve(".pending/recovery");
		Path source = pending.resolve("items/phantom_skiff/OriginalFactory.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "// exact authored factory source\n");
		JsonObject manifest = new JsonObject();
		manifest.addProperty("type", "item");
		manifest.addProperty("identifier", "phantom_skiff");
		manifest.addProperty("sourceDigest", GenerationWorkspace.sourceDigest(pending));
		manifest.addProperty("definitionDigest", legacyDisplayNameDigest(definition, "phantom_skiff"));
		Files.writeString(pending.resolve("manifest.json"), manifest.toString());

		GenerationWorkspace.initialize(world, List.of(definition));

		assertTrue(Files.isRegularFile(world.resolve("items/phantom_skiff/OriginalFactory.java")));
		assertFalse(Files.exists(pending));
	}

	private static String legacyDisplayNameDigest(DynamicContentDefinition definition, String displayName) {
		JsonObject encoded = com.yeyito.littlechemistry.content.DynamicContentJson.encodeDefinition(definition);
		encoded.addProperty("displayName", displayName);
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256");
			return java.util.HexFormat.of().formatHex(digest.digest(encoded.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	@Test
	void catalogCarriesExactPreNormalizationDigestForCrashRecovery() throws Exception {
		DynamicContentDefinition canonical = new DynamicContentDefinition(
				DynamicContentType.ITEM, "odd_vessel", "Odd Vessel", "A deliberately oddly named vessel.",
				DynamicRarity.COMMON, 0L, "0".repeat(64), null, null, null, null,
				DynamicItemProperties.DEFAULT, null, DynamicBehaviorSource.completeLegacySource(null), null);
		JsonObject catalog = com.google.gson.JsonParser.parseString(new String(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(canonical)),
				java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		catalog.addProperty("format", 23);
		catalog.getAsJsonArray("definitions").get(0).getAsJsonObject()
				.addProperty("displayName", "odd--VESSEL");
		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		DynamicContentDefinition normalized = decoded.definitions().getFirst();
		String oldDigest = decoded.recoveryDefinitionDigests().get("odd_vessel").iterator().next();

		Path world = temporaryDirectory.resolve("exact-name-recovery");
		Path pending = world.resolve(".pending/recovery");
		Path source = pending.resolve("items/odd_vessel/OriginalFactory.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "// exact authored factory source\n");
		JsonObject manifest = new JsonObject();
		manifest.addProperty("type", "item");
		manifest.addProperty("identifier", "odd_vessel");
		manifest.addProperty("sourceDigest", GenerationWorkspace.sourceDigest(pending));
		manifest.addProperty("definitionDigest", oldDigest);
		Files.writeString(pending.resolve("manifest.json"), manifest.toString());

		GenerationWorkspace.initialize(world, List.of(normalized), decoded.recoveryDefinitionDigests());

		assertTrue(Files.isRegularFile(world.resolve("items/odd_vessel/OriginalFactory.java")));
		assertFalse(Files.exists(pending));
	}

	@Test
	void oneWordNameCompatibilityCandidatesDoNotCollide() {
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "compass", "Compass", "A generated compass.", DynamicRarity.COMMON,
				0L, "0".repeat(64), null, null, null, null, DynamicItemProperties.DEFAULT, null,
				DynamicBehaviorSource.completeLegacySource(null), null);
		assertDoesNotThrow(() -> GenerationWorkspace.matchesCompatibleDefinitionDigest(
				legacyDisplayNameDigest(definition, "compass"), definition));
	}
}
