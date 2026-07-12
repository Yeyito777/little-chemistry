package com.yeyito.littlechemistry.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class AuthConfig {
	private static final Gson GSON = new Gson();
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE
	);
	private static final Set<PosixFilePermission> SECRET_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	private final Path configDirectory;
	private final Path settingsFile;
	private final Path apiKeyFile;

	public AuthConfig() {
		this(FabricLoader.getInstance().getConfigDir().resolve("little-chemistry"));
	}

	AuthConfig(Path configDirectory) {
		this.configDirectory = configDirectory;
		this.settingsFile = configDirectory.resolve("auth.json");
		this.apiKeyFile = configDirectory.resolve("api-key.txt");
	}

	public synchronized AuthMode mode() {
		if (!Files.isRegularFile(settingsFile)) {
			return AuthMode.SUBSCRIPTION;
		}

		try {
			JsonObject root = GSON.fromJson(Files.readString(settingsFile), JsonObject.class);
			if (root != null && root.has("mode") && "apikey".equalsIgnoreCase(root.get("mode").getAsString())) {
				return AuthMode.API_KEY;
			}
		} catch (Exception ignored) {
			// A missing or invalid setting safely falls back to subscription auth.
		}

		return AuthMode.SUBSCRIPTION;
	}

	public synchronized void useSubscription() throws IOException {
		ensureConfigDirectory();
		writeSettings("subscription");
		Files.deleteIfExists(apiKeyFile);
	}

	public synchronized void useApiKey(String apiKey) throws IOException {
		String normalized = apiKey.trim();
		if (normalized.isEmpty() || normalized.length() > 4096) {
			throw new IllegalArgumentException("The API key is empty or unreasonably long.");
		}

		ensureConfigDirectory();
		atomicWrite(apiKeyFile, normalized + "\n");
		applyPermissions(apiKeyFile, SECRET_PERMISSIONS);
		writeSettings("apikey");
	}

	public synchronized String readApiKey() throws IOException {
		if (!Files.isRegularFile(apiKeyFile)) {
			throw new IOException("API-key auth is selected, but no key is configured. Run /littlechemistry auth apikey <key>.");
		}
		String key = Files.readString(apiKeyFile, StandardCharsets.UTF_8).trim();
		if (key.isEmpty()) {
			throw new IOException("The configured Little Chemistry API key is empty.");
		}
		return key;
	}

	private void writeSettings(String mode) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("mode", mode);
		atomicWrite(settingsFile, GSON.toJson(root) + "\n");
		applyPermissions(settingsFile, SECRET_PERMISSIONS);
	}

	private void ensureConfigDirectory() throws IOException {
		Files.createDirectories(configDirectory);
		applyPermissions(configDirectory, DIRECTORY_PERMISSIONS);
	}

	private static void atomicWrite(Path destination, String contents) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.writeString(temporary, contents, StandardCharsets.UTF_8);
		applyPermissions(temporary, SECRET_PERMISSIONS);
		try {
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) {
		try {
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException | IOException ignored) {
			// POSIX permissions are not available on every supported filesystem.
		}
	}
}
