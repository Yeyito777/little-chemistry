package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicBehavior;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.network.DynamicAssetPayload;
import com.yeyito.littlechemistry.network.DynamicContentPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

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
import java.util.HashSet;
import java.util.Set;
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
				if (decoded.format() < DynamicContentJson.CURRENT_FORMAT) {
					backupLegacyCatalog(dataFile, decoded.format());
				}
				manager = new DynamicContentManager(server, dataFile, decoded.serverId(), decoded.revision(), decoded.definitions());
			} else {
				manager = new DynamicContentManager(server, dataFile, UUID.randomUUID(), 0, List.of());
			}
			manager.save(); // Also upgrades legacy slot-based catalogs to the virtual format.
			manager.rebuildPayload();
			active = manager;
			DynamicContentCatalog.replace(manager.definitions);
			DynamicBehaviorRegistry.replace(manager.definitions);
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
			DynamicBehaviorRegistry.clear();
		}
	}

	public static DynamicContentManager active() {
		return active;
	}

	public boolean belongsTo(MinecraftServer candidate) {
		return server == candidate;
	}

	public DynamicContentDefinition createGenerated(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, GeneratedContentSpec generated)
			throws IOException {
		validateArmorSlot(type, requestedArmorSlot);
		String displayName = normalizeDisplayName(requestedName);
		String name = normalizeIdentifier(displayName);
		if (definitions.stream().anyMatch(definition -> definition.name().equals(name))) {
			throw new IllegalArgumentException("Dynamic content named '" + name + "' already exists.");
		}
		if ((type == DynamicContentType.BLOCK && generated.block() == null)
				|| (type == DynamicContentType.ITEM && generated.item() == null)
				|| (type == DynamicContentType.ARMOR && (generated.armor() == null
						|| generated.armor().slot() != requireArmorSlot(requestedArmorSlot)))) {
			throw new IllegalArgumentException("Generated properties do not match the requested content type");
		}
		Map<String, byte[]> textureAssets = new HashMap<>();
		byte[] textureBytes = generated.texture().renderPng();
		String textureHash = DynamicTextureAsset.sha256(textureBytes);
		textureAssets.put(textureHash, textureBytes);
		if (generated.blockModel() != null) {
			for (DynamicBlockTexture modelTexture : generated.blockModel().textures()) {
				byte[] bytes = modelTexture.texture().renderPng();
				String actualHash = DynamicTextureAsset.sha256(bytes);
				if (!actualHash.equals(modelTexture.hash())) {
					throw new IOException("Generated block model texture hash mismatch for " + modelTexture.id());
				}
				textureAssets.put(actualHash, bytes);
			}
			DynamicBlockTexture primary = generated.blockModel().particleTextureAsset();
			textureHash = primary.hash();
		}
		byte[] armorDisplayTextureBytes = generated.armorDisplayTexture() == null
				? null : generated.armorDisplayTexture().renderPng();
		String armorDisplayTextureHash = armorDisplayTextureBytes == null
				? null : DynamicTextureAsset.sha256(armorDisplayTextureBytes);
		DynamicBehaviorCompiler.Compiled compiledBehavior = DynamicBehaviorRegistry.compile(generated.behaviorSource());
		DynamicBehavior behavior = compiledBehavior.instantiate();
		String behaviorSource = compiledBehavior.source();
		DynamicContentDefinition definition = new DynamicContentDefinition(
				type,
				name,
				displayName,
				generated.description(),
				generated.rarityTier(),
				RANDOM.nextLong(),
				textureHash,
				generated.texture(),
				armorDisplayTextureHash,
				generated.armorDisplayTexture(),
				generated.block(),
				generated.item(),
				generated.armor(),
				behaviorSource,
				generated.blockModel()
		);
		return commit(definition, textureAssets, armorDisplayTextureBytes, compiledBehavior, behavior);
	}

	public DynamicContentDefinition createGenerated(DynamicContentType type, String requestedName,
			GeneratedContentSpec generated) throws IOException {
		return createGenerated(type, generated.armor() == null ? null : generated.armor().slot(), requestedName, generated);
	}

	private DynamicContentDefinition commit(DynamicContentDefinition definition, Map<String, byte[]> textureAssets,
			byte[] armorDisplayTextureBytes,
			DynamicBehaviorCompiler.Compiled compiledBehavior, DynamicBehavior behavior) throws IOException {
		List<DynamicContentDefinition> updatedDefinitions = new ArrayList<>(definitions);
		updatedDefinitions.add(definition);
		long updatedRevision = revision + 1;
		DynamicContentPayload updatedPayload = buildPayload(updatedRevision, updatedDefinitions);
		for (var asset : textureAssets.entrySet()) storeAsset(asset.getKey(), asset.getValue());
		if (armorDisplayTextureBytes != null) {
			storeAsset(definition.armorDisplayTextureHash(), armorDisplayTextureBytes);
		}
		save(updatedRevision, updatedDefinitions);

		definitions.add(definition);
		revision = updatedRevision;
		cachedPayload = updatedPayload;
		DynamicContentCatalog.replace(definitions);
		DynamicBehaviorRegistry.install(definition.name(), compiledBehavior, behavior);
		broadcast();
		return definition;
	}

	public boolean containsName(String requestedName) {
		String name = normalizeIdentifier(normalizeDisplayName(requestedName));
		return definitions.stream().anyMatch(definition -> definition.name().equals(name));
	}

	public DynamicContentDefinition findDefinition(Identifier id) {
		if (id == null || !LittleChemistry.MOD_ID.equals(id.getNamespace())) return null;
		return definitions.stream()
				.filter(definition -> definition.name().equals(id.getPath()))
				.findFirst()
				.orElse(null);
	}

	public int delete(List<String> requestedNames) throws IOException {
		Set<String> names = new HashSet<>(requestedNames);
		if (names.isEmpty() || names.stream().anyMatch(name -> !name.matches("[a-z0-9_]{1,64}"))) {
			throw new IllegalArgumentException("Invalid dynamic content deletion selection");
		}
		List<DynamicContentDefinition> updatedDefinitions = definitions.stream()
				.filter(definition -> !names.contains(definition.name()))
				.toList();
		int deleted = definitions.size() - updatedDefinitions.size();
		if (deleted == 0) {
			throw new IllegalArgumentException("None of the selected content still exists");
		}

		long updatedRevision = revision + 1;
		DynamicContentPayload updatedPayload = buildPayload(updatedRevision, updatedDefinitions);
		save(updatedRevision, updatedDefinitions);
		definitions.clear();
		definitions.addAll(updatedDefinitions);
		revision = updatedRevision;
		cachedPayload = updatedPayload;
		DynamicContentCatalog.replace(definitions);
		DynamicBehaviorRegistry.remove(names);
		AiCraftingManager craftingManager = AiCraftingManager.active();
		if (craftingManager != null) craftingManager.removeRecipesFor(names);
		purgeLoadedReferences(names);
		removeUnusedAssets();
		broadcast();
		return deleted;
	}

	private void purgeLoadedReferences(Set<String> names) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (var slot : player.containerMenu.slots) {
				if (matchesDeleted(slot.getItem(), names)) slot.set(ItemStack.EMPTY);
			}
			if (matchesDeleted(player.containerMenu.getCarried(), names)) {
				player.containerMenu.setCarried(ItemStack.EMPTY);
			}
			player.containerMenu.broadcastChanges();
		}
		for (var level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity && matchesDeleted(itemEntity.getItem(), names)) {
					itemEntity.discard();
				}
				if (entity instanceof LivingEntity livingEntity) {
					for (EquipmentSlot slot : EquipmentSlot.VALUES) {
						if (matchesDeleted(livingEntity.getItemBySlot(slot), names)) {
							livingEntity.setItemSlot(slot, ItemStack.EMPTY);
						}
					}
				}
			}
		}
		DynamicBlockEntity.removeLoaded(server, names);
	}

	private static boolean matchesDeleted(ItemStack stack, Set<String> names) {
		var contentId = stack.get(DynamicContentObjects.CONTENT_ID);
		return contentId != null && LittleChemistry.MOD_ID.equals(contentId.getNamespace()) && names.contains(contentId.getPath());
	}

	private void removeUnusedAssets() {
		Set<String> retained = new HashSet<>();
		for (DynamicContentDefinition definition : definitions) {
			retained.addAll(definition.renderTextureHashes());
			if (definition.armorDisplayTextureHash() != null) retained.add(definition.armorDisplayTextureHash());
		}
		var iterator = assets.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (retained.contains(entry.getKey())) continue;
			iterator.remove();
			try {
				Files.deleteIfExists(entry.getValue());
			} catch (IOException error) {
				LittleChemistry.LOGGER.warn("Could not delete unused Little Chemistry asset {}", entry.getKey(), error);
			}
		}
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
		byte[] textureBytes = definition.texture() == null
				? DynamicTextureAsset.generate(definition.textureSeed())
				: definition.texture().renderPng();
		ensureAsset(definition.textureHash(), textureBytes, definition.name() + " primary texture");
		if (definition.blockModel() != null) {
			for (DynamicBlockTexture texture : definition.blockModel().textures()) {
				if (texture.hash().equals(definition.textureHash())) continue;
				ensureAsset(texture.hash(), texture.texture().renderPng(),
						definition.name() + " block model texture " + texture.id());
			}
		}
		if (definition.armorDisplayTexture() != null) {
			ensureAsset(definition.armorDisplayTextureHash(), definition.armorDisplayTexture().renderPng(),
					definition.name() + " armor display texture");
		}
	}

	private void ensureAsset(String hash, byte[] generated, String description) throws IOException {
		Path destination = dataFile.getParent().resolve("assets").resolve(hash + ".png");
		if (Files.isRegularFile(destination)) {
			byte[] bytes = Files.readAllBytes(destination);
			if (hash.equals(DynamicTextureAsset.sha256(bytes))) {
				assets.put(hash, destination);
				return;
			}
		}
		if (!hash.equals(DynamicTextureAsset.sha256(generated))) {
			throw new IOException("Texture hash mismatch for " + description);
		}
		storeAsset(hash, generated);
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

	public static String normalizeDisplayName(String raw) {
		String displayName = raw == null ? "" : raw.trim();
		if (displayName.length() >= 2 && displayName.startsWith("\"") && displayName.endsWith("\"")) {
			displayName = displayName.substring(1, displayName.length() - 1).trim();
		}
		if (displayName.isBlank() || displayName.length() > 64 || displayName.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Name must contain 1-64 printable characters.");
		}
		return displayName;
	}

	public static String normalizeIdentifier(String displayName) {
		String normalized = displayName.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Name must contain at least one ASCII letter or number.");
		}
		return normalized.length() <= 64 ? normalized : normalized.substring(0, 64).replaceAll("_+$", "");
	}

	private static void backupLegacyCatalog(Path dataFile, int format) throws IOException {
		Path backup = dataFile.resolveSibling("dynamic-content.format-" + format + ".backup.json");
		if (!Files.exists(backup)) {
			Files.copy(dataFile, backup);
			LittleChemistry.LOGGER.info("Backed up legacy Little Chemistry catalog to {} before format migration", backup);
		}
	}

	private static DynamicArmorSlot requireArmorSlot(DynamicArmorSlot slot) {
		if (slot == null) throw new IllegalArgumentException("Armor content must specify head, chest, leggings, or boots");
		return slot;
	}

	private static void validateArmorSlot(DynamicContentType type, DynamicArmorSlot slot) {
		if ((type == DynamicContentType.ARMOR) != (slot != null)) {
			throw new IllegalArgumentException("Armor content must specify head, chest, leggings, or boots");
		}
	}

}
