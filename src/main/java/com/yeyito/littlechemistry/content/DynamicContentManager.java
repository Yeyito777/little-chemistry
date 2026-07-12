package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.network.DynamicAssetPayload;
import com.yeyito.littlechemistry.network.DynamicContentPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DynamicContentManager {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ExecutorService ASSET_IO = Executors.newVirtualThreadPerTaskExecutor();
	private static volatile DynamicContentManager active;

	private final MinecraftServer server;
	private final Path dataFile;
	private final UUID serverId;
	private long revision;
	private final List<DynamicContentDefinition> definitions;
	private final Map<String, Path> assets = new HashMap<>();
	private DynamicContentPayload cachedPayload;

	private DynamicContentManager(MinecraftServer server, Path dataFile, UUID serverId, long revision,
			List<DynamicContentDefinition> definitions) {
		this.server = server;
		this.dataFile = dataFile;
		this.serverId = serverId;
		this.revision = revision;
		this.definitions = new ArrayList<>(definitions);
		for (DynamicContentDefinition definition : definitions) {
			try {
				ensureAsset(definition);
			} catch (IOException error) {
				throw new IllegalStateException("Could not rebuild texture for " + definition.name(), error);
			}
		}
	}

	public static void start(MinecraftServer server) {
		Path dataFile = server.getWorldPath(LevelResource.ROOT)
				.resolve("little-chemistry")
				.resolve("dynamic-content.json");
		try {
			DynamicContentManager manager;
			if (Files.isRegularFile(dataFile)) {
				DynamicContentJson.Decoded decoded = DynamicContentJson.decode(Files.readAllBytes(dataFile));
				manager = new DynamicContentManager(server, dataFile, decoded.serverId(), decoded.revision(), decoded.definitions());
			} else {
				manager = new DynamicContentManager(server, dataFile, UUID.randomUUID(), 0, List.of());
			}
			manager.save(); // Also upgrades legacy slot-based catalogs to the virtual format.
			manager.rebuildPayload();
			active = manager;
			DynamicContentCatalog.replace(manager.definitions);
			LittleChemistry.LOGGER.info("Loaded {} dynamic Little Chemistry entries", manager.definitions.size());
		} catch (Exception error) {
			throw new IllegalStateException("Could not load Little Chemistry dynamic content", error);
		}
	}

	public static void stop(MinecraftServer server) {
		DynamicContentManager manager = active;
		if (manager != null && manager.server == server) {
			active = null;
			DynamicContentCatalog.clear();
		}
	}

	public static DynamicContentManager active() {
		return active;
	}

	public DynamicContentDefinition create(DynamicContentType type, String requestedName) throws IOException {
		String displayName = normalizeDisplayName(requestedName);
		String name = normalizeIdentifier(displayName);
		if (definitions.stream().anyMatch(definition -> definition.name().equals(name))) {
			throw new IllegalArgumentException("Dynamic content named '" + name + "' already exists.");
		}

		long textureSeed = RANDOM.nextLong();
		byte[] textureBytes = DynamicTextureAsset.generate(textureSeed);
		String textureHash = DynamicTextureAsset.sha256(textureBytes);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				type, name, displayName, textureSeed, textureHash
		);
		List<DynamicContentDefinition> updatedDefinitions = new ArrayList<>(definitions);
		updatedDefinitions.add(definition);
		long updatedRevision = revision + 1;
		DynamicContentPayload updatedPayload = buildPayload(updatedRevision, updatedDefinitions);
		storeAsset(textureHash, textureBytes);
		save(updatedRevision, updatedDefinitions);

		definitions.add(definition);
		revision = updatedRevision;
		cachedPayload = updatedPayload;
		DynamicContentCatalog.replace(definitions);
		broadcast();
		return definition;
	}

	public void sendSnapshot(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, DynamicContentPayload.TYPE)) {
			ServerPlayNetworking.send(player, cachedPayload);
		} else {
			LittleChemistry.LOGGER.warn("Player {} cannot receive Little Chemistry dynamic content; is the client mod installed?",
					player.getGameProfile().name());
		}
	}

	public void sendAssets(ServerPlayer player, List<String> hashes) {
		Map<String, Path> requested = new HashMap<>();
		for (String hash : hashes) {
			Path asset = assets.get(hash);
			if (asset != null) {
				requested.put(hash, asset);
			}
		}
		UUID playerId = player.getUUID();
		CompletableFuture.supplyAsync(() -> {
			List<DynamicAssetPayload> payloads = new ArrayList<>(requested.size());
			for (var entry : requested.entrySet()) {
				try {
					byte[] bytes = Files.readAllBytes(entry.getValue());
					if (entry.getKey().equals(DynamicTextureAsset.sha256(bytes))) {
						payloads.add(new DynamicAssetPayload(entry.getKey(), bytes));
					}
				} catch (IOException error) {
					LittleChemistry.LOGGER.error("Could not read Little Chemistry asset {}", entry.getKey(), error);
				}
			}
			return payloads;
		}, ASSET_IO).thenAccept(payloads -> server.execute(() -> {
			ServerPlayer recipient = server.getPlayerList().getPlayer(playerId);
			if (recipient == null || !ServerPlayNetworking.canSend(recipient, DynamicAssetPayload.TYPE)) {
				return;
			}
			for (DynamicAssetPayload payload : payloads) {
				ServerPlayNetworking.send(recipient, payload);
			}
		}));
	}

	private void storeAsset(String hash, byte[] bytes) throws IOException {
		Path directory = dataFile.getParent().resolve("assets");
		Files.createDirectories(directory);
		Path destination = directory.resolve(hash + ".png");
		if (!Files.isRegularFile(destination)
				|| !hash.equals(DynamicTextureAsset.sha256(Files.readAllBytes(destination)))) {
			Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
			Files.write(temporary, bytes);
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		assets.put(hash, destination);
	}

	private void ensureAsset(DynamicContentDefinition definition) throws IOException {
		Path destination = dataFile.getParent().resolve("assets").resolve(definition.textureHash() + ".png");
		if (Files.isRegularFile(destination)) {
			byte[] bytes = Files.readAllBytes(destination);
			if (definition.textureHash().equals(DynamicTextureAsset.sha256(bytes))) {
				assets.put(definition.textureHash(), destination);
				return;
			}
		}
		byte[] generated = DynamicTextureAsset.generate(definition.textureSeed());
		if (!definition.textureHash().equals(DynamicTextureAsset.sha256(generated))) {
			throw new IOException("Texture hash mismatch for " + definition.name());
		}
		storeAsset(definition.textureHash(), generated);
	}

	private void broadcast() {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendSnapshot(player);
		}
	}

	private void save() throws IOException {
		save(revision, definitions);
	}

	private void save(long savedRevision, List<DynamicContentDefinition> savedDefinitions) throws IOException {
		Files.createDirectories(dataFile.getParent());
		byte[] bytes = DynamicContentJson.encode(serverId, savedRevision, savedDefinitions);
		Path temporary = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
		Files.write(temporary, bytes);
		try {
			Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void rebuildPayload() throws IOException {
		cachedPayload = buildPayload(revision, definitions);
	}

	private DynamicContentPayload buildPayload(long payloadRevision, List<DynamicContentDefinition> payloadDefinitions)
			throws IOException {
		byte[] json = DynamicContentJson.encode(serverId, payloadRevision, payloadDefinitions);
		if (json.length > DynamicContentPayload.MAX_DEFINITIONS_BYTES) {
			throw new IOException("Dynamic content catalog exceeds the network safety limit");
		}
		return new DynamicContentPayload(serverId, payloadRevision, json);
	}

	private static String normalizeDisplayName(String raw) {
		String displayName = raw == null ? "" : raw.trim();
		if (displayName.length() >= 2 && displayName.startsWith("\"") && displayName.endsWith("\"")) {
			displayName = displayName.substring(1, displayName.length() - 1).trim();
		}
		if (displayName.isBlank() || displayName.length() > 64 || displayName.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Name must contain 1-64 printable characters.");
		}
		return displayName;
	}

	private static String normalizeIdentifier(String displayName) {
		String normalized = displayName.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Name must contain at least one ASCII letter or number.");
		}
		return normalized.length() <= 64 ? normalized : normalized.substring(0, 64).replaceAll("_+$", "");
	}

}
