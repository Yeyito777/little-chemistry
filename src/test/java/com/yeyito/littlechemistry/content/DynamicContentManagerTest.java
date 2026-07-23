package com.yeyito.littlechemistry.content;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicContentManagerTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void newerDevelopmentCatalogRestoresLatestCompatibleBackupWithoutLosingNewerBytes() throws Exception {
		Path dataFile = temporaryDirectory.resolve("dynamic-content.json");
		byte[] compatible = DynamicContentJson.encode(UUID.randomUUID(), 7, List.of());
		Files.write(temporaryDirectory.resolve("dynamic-content.format-17.backup.json"), legacyFormat(compatible, 17));
		Path selected = temporaryDirectory.resolve("dynamic-content.format-19.backup.json");
		Files.write(selected, legacyFormat(compatible, 19));
		byte[] newer = legacyFormat(compatible, 21);
		Files.write(dataFile, newer);

		DynamicContentJson.Decoded restored = DynamicContentManager.loadCompatibleCatalog(dataFile);

		assertEquals(19, restored.format());
		assertEquals(7, restored.revision());
		assertArrayEquals(newer, Files.readAllBytes(
				temporaryDirectory.resolve("dynamic-content.format-21.pre-v3.5-revert.json")));
		assertArrayEquals(newer, Files.readAllBytes(dataFile),
				"the normal manager save, not catalog selection, performs the atomic downgrade");

		byte[] newerAgain = legacyFormat(DynamicContentJson.encode(UUID.randomUUID(), 8, List.of()), 21);
		Files.write(dataFile, newerAgain);
		DynamicContentManager.loadCompatibleCatalog(dataFile);
		List<byte[]> archives;
		try (var paths = Files.list(temporaryDirectory)) {
			archives = paths.filter(path -> path.getFileName().toString().startsWith(
					"dynamic-content.format-21.pre-v3.5-revert"))
					.map(path -> {
						try {
							return Files.readAllBytes(path);
						} catch (IOException failure) {
							throw new java.io.UncheckedIOException(failure);
						}
					}).toList();
		}
		assertEquals(2, archives.size());
		assertTrue(archives.stream().anyMatch(bytes -> java.util.Arrays.equals(newer, bytes)));
		assertTrue(archives.stream().anyMatch(bytes -> java.util.Arrays.equals(newerAgain, bytes)));
	}

	@Test
	void newerCatalogWithoutCompatibleBackupFailsWithRecoveryGuidance() throws Exception {
		Path dataFile = temporaryDirectory.resolve("dynamic-content.json");
		byte[] compatible = DynamicContentJson.encode(UUID.randomUUID(), 0, List.of());
		Files.write(dataFile, legacyFormat(compatible, 21));

		IOException failure = assertThrows(IOException.class,
				() -> DynamicContentManager.loadCompatibleCatalog(dataFile));

		assertTrue(failure.getMessage().contains("no compatible migration backup"));
	}

	@Test
	void abandonedFormatTwentyIsArchivedAndRestoredFromFormatNineteen() throws Exception {
		Path dataFile = temporaryDirectory.resolve("dynamic-content.json");
		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 4, List.of());
		byte[] formatNineteen = legacyFormat(encoded, 19);
		byte[] formatTwenty = legacyFormat(encoded, 20);
		Files.write(temporaryDirectory.resolve("dynamic-content.format-19.backup.json"), formatNineteen);
		Files.write(dataFile, formatTwenty);

		DynamicContentJson.Decoded restored = DynamicContentManager.loadCompatibleCatalog(dataFile);

		assertEquals(19, restored.format());
		assertArrayEquals(formatTwenty, Files.readAllBytes(
				temporaryDirectory.resolve("dynamic-content.format-20.pre-v3.5-revert.json")));
	}

	private static byte[] legacyFormat(byte[] encoded, int format) {
		var root = JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		root.addProperty("format", format);
		return root.toString().getBytes(StandardCharsets.UTF_8);
	}
}
