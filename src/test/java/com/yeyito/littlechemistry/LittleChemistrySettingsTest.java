package com.yeyito.littlechemistry;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LittleChemistrySettingsTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void initializesExocortexLoggingOnByDefault() throws Exception {
		LittleChemistrySettings settings = new LittleChemistrySettings(temporaryDirectory);

		assertTrue(settings.exocortexLogging());
		settings.initialize();

		var stored = JsonParser.parseString(Files.readString(temporaryDirectory.resolve("settings.json")))
				.getAsJsonObject();
		assertTrue(stored.get(LittleChemistrySettings.EXOCORTEX_LOGGING).getAsBoolean());
	}

	@Test
	void persistsOffAndPreservesOtherSettings() throws Exception {
		Files.writeString(temporaryDirectory.resolve("settings.json"),
				"{\"future-setting\":17,\"exocortex-logging\":true}\n");
		LittleChemistrySettings settings = new LittleChemistrySettings(temporaryDirectory);

		settings.setExocortexLogging(false);
		settings.initialize();

		assertFalse(settings.exocortexLogging());
		var stored = JsonParser.parseString(Files.readString(temporaryDirectory.resolve("settings.json")))
				.getAsJsonObject();
		assertTrue(stored.has("future-setting"));
		assertFalse(stored.get(LittleChemistrySettings.EXOCORTEX_LOGGING).getAsBoolean());
	}

	@Test
	void invalidSettingsSafelyReturnToTheOnDefault() throws Exception {
		Files.writeString(temporaryDirectory.resolve("settings.json"), "not json");
		LittleChemistrySettings settings = new LittleChemistrySettings(temporaryDirectory);

		assertTrue(settings.exocortexLogging());
		settings.initialize();
		assertTrue(settings.exocortexLogging());
	}
}
