package com.yeyito.littlechemistry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Persistent server-side mod settings shared by every world in this Minecraft installation. */
public final class LittleChemistrySettings {
	public static final String EXOCORTEX_LOGGING = "exocortex-logging";
	private static final boolean DEFAULT_EXOCORTEX_LOGGING = true;
	private static final Gson GSON = new Gson();

	private final Path settingsFile;

	public LittleChemistrySettings() {
		this(FabricLoader.getInstance().getConfigDir().resolve("little-chemistry"));
	}

	LittleChemistrySettings(Path configDirectory) {
		this.settingsFile = configDirectory.resolve("settings.json");
	}

	/** Creates the settings file with defaults while preserving any existing valid settings. */
	public synchronized void initialize() throws IOException {
		JsonObject settings = readForUpdate();
		JsonElement configured = settings.get(EXOCORTEX_LOGGING);
		if (configured != null && configured.isJsonPrimitive()
				&& configured.getAsJsonPrimitive().isBoolean()) return;
		settings.addProperty(EXOCORTEX_LOGGING, DEFAULT_EXOCORTEX_LOGGING);
		write(settings);
	}

	/** Returns the configured value, safely defaulting to on when the file is missing or invalid. */
	public synchronized boolean exocortexLogging() {
		try {
			JsonElement configured = readForUpdate().get(EXOCORTEX_LOGGING);
			return configured != null && configured.isJsonPrimitive()
					&& configured.getAsJsonPrimitive().isBoolean()
					? configured.getAsBoolean() : DEFAULT_EXOCORTEX_LOGGING;
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT_EXOCORTEX_LOGGING;
		}
	}

	public synchronized void setExocortexLogging(boolean enabled) throws IOException {
		JsonObject settings = readForUpdate();
		settings.addProperty(EXOCORTEX_LOGGING, enabled);
		write(settings);
	}

	private JsonObject readForUpdate() throws IOException {
		if (!Files.isRegularFile(settingsFile)) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(settingsFile, StandardCharsets.UTF_8));
			return parsed instanceof JsonObject object ? object : new JsonObject();
		} catch (RuntimeException ignored) {
			return new JsonObject();
		}
	}

	private void write(JsonObject settings) throws IOException {
		Files.createDirectories(settingsFile.getParent());
		Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp-" + UUID.randomUUID());
		Files.writeString(temporary, GSON.toJson(settings) + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
		try {
			Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
