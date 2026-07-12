package com.yeyito.littlechemistry.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SubscriptionCredentials {
	private static final Gson GSON = new Gson();
	private static final Pattern CODEX_STORE_MODE = Pattern.compile(
			"(?m)^\\s*cli_auth_credentials_store\\s*=\\s*[\"'](file|keyring|auto|ephemeral)[\"']"
	);
	private static final int MAX_CREDENTIAL_FILE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_KEYRING_VALUE_BYTES = 4 * 1024 * 1024;

	private SubscriptionCredentials() {
	}

	static OpenAiCredentials loadFresh() throws IOException {
		Optional<OpenAiCredentials> exocortex = loadFromExocortex();
		if (exocortex.isPresent()) {
			return exocortex.get();
		}

		Optional<OpenAiCredentials> codex = loadFromCodex();
		if (codex.isPresent()) {
			return codex.get();
		}

		throw new IOException(
				"No OpenAI subscription session was found. Log in through Exocortex or Codex CLI, " +
				"or run /littlechemistry auth apikey <key>."
		);
	}

	private static Optional<OpenAiCredentials> loadFromExocortex() {
		for (Path candidate : exocortexCredentialCandidates()) {
			Optional<JsonObject> root = readJsonObject(candidate);
			if (root.isEmpty()) {
				continue;
			}

			JsonObject providers = object(root.get(), "providers");
			JsonObject openai = providers == null ? null : object(providers, "openai");
			if (openai == null) {
				continue;
			}

			JsonObject selected = selectedExocortexAccount(openai);
			String token = nestedString(selected, "tokens", "accessToken");
			if (isBlank(token)) {
				continue;
			}

			String accountId = string(selected, "accountId");
			if (isBlank(accountId)) {
				accountId = nestedString(selected, "profile", "accountUuid");
			}
			return Optional.of(new OpenAiCredentials(AuthMode.SUBSCRIPTION, token, blankToNull(accountId), "Exocortex"));
		}

		return Optional.empty();
	}

	private static JsonObject selectedExocortexAccount(JsonObject openai) {
		JsonArray accounts = array(openai, "accounts");
		if (accounts == null || accounts.isEmpty()) {
			return openai;
		}

		int index = integer(openai, "currentIndex", 0);
		if (index < 0 || index >= accounts.size()) {
			index = 0;
		}
		JsonElement selected = accounts.get(index);
		return selected.isJsonObject() ? selected.getAsJsonObject() : openai;
	}

	private static List<Path> exocortexCredentialCandidates() {
		Path home = Path.of(System.getProperty("user.home"));
		LinkedHashSet<Path> candidates = new LinkedHashSet<>();
		addPathFromEnvironment(candidates, "LITTLE_CHEMISTRY_EXOCORTEX_CREDENTIALS", false);
		addPathFromEnvironment(candidates, "EXOCORTEX_CONFIG_DIR", true);
		candidates.add(home.resolve(".config/exocortex/secrets/credentials.json"));
		candidates.add(home.resolve("Workspace/exocortex/config/secrets/credentials.json"));
		candidates.add(home.resolve("Workspace/Exocortex/config/secrets/credentials.json"));
		return List.copyOf(candidates);
	}

	private static void addPathFromEnvironment(LinkedHashSet<Path> candidates, String name, boolean configDirectory) {
		String raw = System.getenv(name);
		if (isBlank(raw)) {
			return;
		}
		Path path = Path.of(raw);
		candidates.add(configDirectory ? path.resolve("secrets/credentials.json") : path);
	}

	private static Optional<OpenAiCredentials> loadFromCodex() {
		Path codexHome = codexHome();
		String mode = codexStorageMode(codexHome);

		if (!"file".equals(mode) && !"ephemeral".equals(mode)) {
			Optional<JsonObject> fromKeyring = readCodexDirectKeyring(codexHome);
			Optional<OpenAiCredentials> credentials = fromKeyring.flatMap(root -> parseCodexAuth(root, "Codex CLI keyring"));
			if (credentials.isPresent()) {
				return credentials;
			}
		}

		if (!"keyring".equals(mode) && !"ephemeral".equals(mode)) {
			Optional<JsonObject> fromFile = readJsonObject(codexHome.resolve("auth.json"));
			Optional<OpenAiCredentials> credentials = fromFile.flatMap(root -> parseCodexAuth(root, "Codex CLI"));
			if (credentials.isPresent()) {
				return credentials;
			}
		}

		return Optional.empty();
	}

	private static Path codexHome() {
		String configured = System.getenv("CODEX_HOME");
		return isBlank(configured)
				? Path.of(System.getProperty("user.home")).resolve(".codex")
				: Path.of(configured);
	}

	private static String codexStorageMode(Path codexHome) {
		Path config = codexHome.resolve("config.toml");
		if (!Files.isRegularFile(config)) {
			return "auto";
		}
		try {
			String contents = Files.readString(config, StandardCharsets.UTF_8);
			Matcher matcher = CODEX_STORE_MODE.matcher(contents);
			return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "auto";
		} catch (IOException ignored) {
			return "auto";
		}
	}

	private static Optional<JsonObject> readCodexDirectKeyring(Path codexHome) {
		String account = "cli|" + sha256Hex(canonicalString(codexHome)).substring(0, 16);
		Optional<String> modern = runSecretTool("Codex Auth", account, true);
		if (modern.isPresent()) {
			return parseJsonObject(modern.get(), "Codex keyring");
		}
		return runSecretTool("Codex Auth", account, false)
				.flatMap(value -> parseJsonObject(value, "Codex legacy keyring"));
	}

	private static Optional<String> runSecretTool(String service, String username, boolean includeTarget) {
		List<String> command = new ArrayList<>(List.of(
				"secret-tool", "lookup",
				"service", service,
				"username", username
		));
		if (includeTarget) {
			command.add("target");
			command.add("default");
		}

		try {
			Process process = new ProcessBuilder(command)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			process.getOutputStream().close();
			if (!process.waitFor(3, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return Optional.empty();
			}
			if (process.exitValue() != 0) {
				return Optional.empty();
			}
			byte[] bytes = process.getInputStream().readNBytes(MAX_KEYRING_VALUE_BYTES + 1);
			if (bytes.length == 0 || bytes.length > MAX_KEYRING_VALUE_BYTES) {
				return Optional.empty();
			}
			return Optional.of(new String(bytes, StandardCharsets.UTF_8).trim());
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} catch (IOException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<OpenAiCredentials> parseCodexAuth(JsonObject root, String source) {
		JsonObject tokens = object(root, "tokens");
		if (tokens == null) {
			return Optional.empty();
		}
		String token = string(tokens, "access_token");
		if (isBlank(token)) {
			return Optional.empty();
		}
		String accountId = string(tokens, "account_id");
		return Optional.of(new OpenAiCredentials(AuthMode.SUBSCRIPTION, token, blankToNull(accountId), source));
	}

	private static Optional<JsonObject> readJsonObject(Path path) {
		try {
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_CREDENTIAL_FILE_BYTES) {
				return Optional.empty();
			}
			return parseJsonObject(Files.readString(path, StandardCharsets.UTF_8), path.toString());
		} catch (IOException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<JsonObject> parseJsonObject(String contents, String source) {
		try {
			JsonObject root = GSON.fromJson(contents, JsonObject.class);
			return Optional.ofNullable(root);
		} catch (Exception error) {
			LittleChemistry.LOGGER.warn("Could not parse {} subscription credentials", source);
			return Optional.empty();
		}
	}

	private static String canonicalString(Path path) {
		try {
			return path.toRealPath().toString();
		} catch (IOException ignored) {
			return path.toAbsolutePath().normalize().toString();
		}
	}

	private static String sha256Hex(String input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		JsonElement value = parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	private static String nestedString(JsonObject parent, String objectKey, String valueKey) {
		JsonObject nested = object(parent, objectKey);
		return nested == null ? null : string(nested, valueKey);
	}

	private static String string(JsonObject parent, String key) {
		JsonElement value = parent.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return null;
		}
		try {
			return value.getAsString();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static int integer(JsonObject parent, String key, int fallback) {
		JsonElement value = parent.get(key);
		try {
			return value == null ? fallback : value.getAsInt();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value;
	}
}
