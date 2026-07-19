package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicParticleDefinition;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RuntimeTextureStore {
	private static final ExecutorService DECODER = Executors.newVirtualThreadPerTaskExecutor();
	private static final Map<String, Identifier> loadedTextures = new HashMap<>();
	private static final Map<String, List<Identifier>> loadedArmorTextures = new HashMap<>();
	private static final Map<String, boolean[]> opaquePixels = new HashMap<>();
	private static final Map<String, CompletableFuture<Void>> loadingTextures = new HashMap<>();
	private static final Map<String, Integer> retiredParticleTextureTicks = new HashMap<>();
	private static Set<String> expectedHashes = Set.of();
	private static Set<String> expectedItemHashes = Set.of();
	private static Set<String> expectedExtrudedItemHashes = Set.of();
	private static Set<String> expectedArmorHashes = Set.of();
	private static Set<String> expectedParticleHashes = Set.of();
	private static UUID activeServer;

	private RuntimeTextureStore() {
	}

	public static void beginServer(Minecraft client, UUID serverId) {
		if (serverId.equals(activeServer)) {
			return;
		}
		clear(client);
		activeServer = serverId;
	}

	/**
	 * Inspects and decodes a catalog revision without changing the textures visible to the current catalog.
	 * The returned preparation must either be committed or closed on the client thread.
	 */
	public static CompletableFuture<Preparation> prepare(Minecraft client, UUID serverId,
			List<DynamicContentDefinition> definitions) throws IOException {
		if (!client.isSameThread()) throw new IllegalStateException("Runtime textures must be prepared on the client thread");
		beginServer(client, serverId);
		Set<String> particleHashes = definitions.stream()
				.flatMap(definition -> definition.customParticleTextureHashes().stream())
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		Set<String> ordinaryAndParticleHashes = new HashSet<>();
		for (DynamicContentDefinition definition : definitions) {
			ordinaryAndParticleHashes.addAll(definition.renderTextureHashes());
		}
		ordinaryAndParticleHashes.addAll(particleHashes);
		Set<String> itemHashes = Set.copyOf(ordinaryAndParticleHashes);
		Set<String> extrudedItemHashes = definitions.stream()
				.filter(definition -> definition.type() != DynamicContentType.BLOCK)
				.map(DynamicContentDefinition::textureHash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		Set<String> armorHashes = definitions.stream()
				.filter(definition -> definition.type() == DynamicContentType.ARMOR)
				.map(DynamicContentDefinition::effectiveArmorDisplayTextureHash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		Set<String> allExpectedHashes = new HashSet<>(itemHashes);
		allExpectedHashes.addAll(armorHashes);
		Target target = new Target(Set.copyOf(allExpectedHashes), itemHashes,
				extrudedItemHashes, armorHashes, particleHashes);
		Path assetDirectory = assetDirectory(serverId);
		Files.createDirectories(assetDirectory);
		List<DynamicContentDefinition> definitionSnapshot = List.copyOf(definitions);
		Map<String, Roles> hashesToPrepare = new HashMap<>();
		for (String hash : target.expectedHashes()) {
			boolean loadItem = target.itemHashes().contains(hash) && !loadedTextures.containsKey(hash);
			boolean loadOpacity = target.extrudedItemHashes().contains(hash) && opaquePixels.get(hash) == null;
			boolean loadArmor = target.armorHashes().contains(hash) && !loadedArmorTextures.containsKey(hash);
			if (loadItem || loadOpacity || loadArmor) {
				hashesToPrepare.put(hash, new Roles(loadItem, loadOpacity, loadArmor));
			}
		}

		return CompletableFuture.supplyAsync(() -> {
			Map<String, DecodedTexture> decodedAssets = new HashMap<>();
			List<String> missing = new ArrayList<>();
			try {
				for (var entry : hashesToPrepare.entrySet()) {
					String hash = entry.getKey();
					byte[] bytes = cachedOrReconstructed(assetDirectory, definitionSnapshot, hash);
					if (bytes == null) {
						missing.add(hash);
						continue;
					}
					decodedAssets.put(hash, decodePrepared(bytes, entry.getValue()));
				}
				return new Preparation(serverId, target, decodedAssets, List.copyOf(missing));
			} catch (Throwable error) {
				for (DecodedTexture decoded : decodedAssets.values()) decoded.close();
				throw new RuntimeException("Could not prepare Little Chemistry runtime textures", error);
			}
		}, DECODER);
	}

	/** Installs a fully decoded preparation and swaps the catalog in the same client-thread transaction. */
	public static List<String> commit(Minecraft client, UUID serverId, Preparation preparation,
			Runnable catalogCommit) {
		if (!client.isSameThread()) throw new IllegalStateException("Runtime textures must be committed on the client thread");
		if (!serverId.equals(activeServer) || !serverId.equals(preparation.serverId())) {
			preparation.close();
			throw new IllegalStateException("Runtime texture preparation belongs to an inactive server");
		}
		preparation.claim();

		Target target = preparation.target();
		Map<String, Identifier> addedItems = new HashMap<>();
		Map<String, List<Identifier>> addedArmor = new HashMap<>();
		Map<String, boolean[]> addedOpacity = new HashMap<>();
		List<Identifier> registered = new ArrayList<>();
		Set<String> previousHashes = expectedHashes;
		Set<String> previousItems = expectedItemHashes;
		Set<String> previousExtrudedItems = expectedExtrudedItemHashes;
		Set<String> previousArmor = expectedArmorHashes;
		Set<String> previousParticles = expectedParticleHashes;

		try {
			for (var entry : preparation.decodedAssets().entrySet()) {
				String hash = entry.getKey();
				DecodedTexture decoded = entry.getValue();
				if (decoded.image() != null && target.itemHashes().contains(hash)
						&& !loadedTextures.containsKey(hash)) {
					Identifier textureId = LittleChemistry.id("runtime/" + hash);
					register(client, textureId, "Little Chemistry " + hash, decoded.takeImage(), registered);
					addedItems.put(hash, textureId);
				}
				if (decoded.opaquePixels() != null && target.extrudedItemHashes().contains(hash)
						&& opaquePixels.get(hash) == null) {
					addedOpacity.put(hash, decoded.opaquePixels());
				}
				if (decoded.humanoid() != null && target.armorHashes().contains(hash)
						&& !loadedArmorTextures.containsKey(hash)) {
					List<Identifier> ids = armorTextureIds(hash);
					register(client, ids.get(0), "Little Chemistry armor " + hash,
							decoded.takeHumanoid(), registered);
					register(client, ids.get(1), "Little Chemistry armor leggings " + hash,
							decoded.takeLeggings(), registered);
					register(client, ids.get(2), "Little Chemistry baby armor " + hash,
							decoded.takeBaby(), registered);
					addedArmor.put(hash, ids);
				}
			}

			loadedTextures.putAll(addedItems);
			loadedArmorTextures.putAll(addedArmor);
			opaquePixels.putAll(addedOpacity);
			expectedHashes = target.expectedHashes();
			expectedItemHashes = target.itemHashes();
			expectedExtrudedItemHashes = target.extrudedItemHashes();
			expectedArmorHashes = target.armorHashes();
			expectedParticleHashes = target.particleHashes();
			catalogCommit.run();
		} catch (Throwable error) {
			expectedHashes = previousHashes;
			expectedItemHashes = previousItems;
			expectedExtrudedItemHashes = previousExtrudedItems;
			expectedArmorHashes = previousArmor;
			expectedParticleHashes = previousParticles;
			for (var entry : addedItems.entrySet()) loadedTextures.remove(entry.getKey(), entry.getValue());
			for (var entry : addedArmor.entrySet()) loadedArmorTextures.remove(entry.getKey(), entry.getValue());
			for (String hash : addedOpacity.keySet()) opaquePixels.remove(hash);
			for (Identifier texture : registered) safeRelease(client, texture);
			preparation.closeAfterClaim();
			if (error instanceof RuntimeException runtimeError) throw runtimeError;
			if (error instanceof Error fatalError) throw fatalError;
			throw new RuntimeException(error);
		}

		preparation.closeAfterClaim();
		retireObsolete(client, previousParticles, target);
		DynamicParticleTextures.clear();
		return preparation.missing();
	}

	private static byte[] cachedOrReconstructed(Path assetDirectory,
			List<DynamicContentDefinition> definitions, String hash) {
		Path cached = assetDirectory.resolve(hash + ".png");
		try {
			if (Files.isRegularFile(cached)) {
				byte[] bytes = Files.readAllBytes(cached);
				if (hash.equals(DynamicTextureAsset.sha256(bytes))) return bytes;
				Files.deleteIfExists(cached);
			}
		} catch (IOException error) {
			LittleChemistry.LOGGER.warn("Could not read cached Little Chemistry texture {}", hash, error);
		}
		try {
			byte[] reconstructed = reconstruct(definitions, hash);
			if (reconstructed != null && hash.equals(DynamicTextureAsset.sha256(reconstructed))) {
				try {
					cache(assetDirectory, hash, reconstructed);
				} catch (IOException error) {
					LittleChemistry.LOGGER.warn("Could not cache reconstructed Little Chemistry texture {}", hash, error);
				}
				return reconstructed;
			}
		} catch (IOException error) {
			LittleChemistry.LOGGER.warn("Could not reconstruct Little Chemistry texture {} from its definition", hash, error);
		}
		return null;
	}

	private static DecodedTexture decodePrepared(byte[] bytes, Roles roles) throws IOException {
		NativeImage source = NativeImage.read(bytes);
		NativeImage humanoid = null;
		NativeImage leggings = null;
		NativeImage baby = null;
		try {
			if (roles.loadOpacity() && (source.getWidth() != DynamicTextureAsset.WIDTH
					|| source.getHeight() != DynamicTextureAsset.HEIGHT)) {
				throw new IOException("Runtime inventory texture must be 16x16 pixels");
			}
			boolean[] opacity = roles.loadOpacity() ? opacity(source) : null;
			if (roles.loadArmor()) {
				if (source.getWidth() == DynamicArmorDisplayTextureSpec.WIDTH
						&& source.getHeight() == DynamicArmorDisplayTextureSpec.HEIGHT) {
					humanoid = copyTexture(source);
					leggings = copyTexture(source);
					baby = babyArmorTexture(source);
				} else if (source.getWidth() == DynamicTextureAsset.WIDTH
						&& source.getHeight() == DynamicTextureAsset.HEIGHT) {
					// Format 6 and older used the inventory icon for the worn layer.
					humanoid = legacyArmorTexture(source, 64, 32);
					leggings = legacyArmorTexture(source, 64, 32);
					baby = legacyArmorTexture(source, 64, 64);
				} else {
					throw new IOException("Runtime armor display texture must be 64x32 pixels");
				}
			}
			NativeImage itemImage = roles.loadItem() ? source : null;
			if (!roles.loadItem()) source.close();
			return new DecodedTexture(itemImage, opacity, humanoid, leggings, baby);
		} catch (Throwable error) {
			source.close();
			if (humanoid != null) humanoid.close();
			if (leggings != null) leggings.close();
			if (baby != null) baby.close();
			if (error instanceof IOException ioError) throw ioError;
			if (error instanceof RuntimeException runtimeError) throw runtimeError;
			if (error instanceof Error fatalError) throw fatalError;
			throw new RuntimeException(error);
		}
	}

	private static void register(Minecraft client, Identifier id, String label, NativeImage image,
			List<Identifier> registered) {
		DynamicTexture texture = new DynamicTexture(() -> label, image);
		try {
			client.getTextureManager().register(id, texture);
			registered.add(id);
		} catch (Throwable error) {
			texture.close();
			if (error instanceof RuntimeException runtimeError) throw runtimeError;
			if (error instanceof Error fatalError) throw fatalError;
			throw new RuntimeException(error);
		}
	}

	private static List<Identifier> armorTextureIds(String hash) {
		return List.of(
				DynamicArmorAssets.textureLocation(hash,
						net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID),
				DynamicArmorAssets.textureLocation(hash,
						net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS),
				DynamicArmorAssets.textureLocation(hash,
						net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_BABY));
	}

	private static void retireObsolete(Minecraft client, Set<String> previousParticleHashes, Target target) {
		retiredParticleTextureTicks.keySet().removeAll(target.itemHashes());
		var loadedIterator = loadedTextures.entrySet().iterator();
		while (loadedIterator.hasNext()) {
			var entry = loadedIterator.next();
			if (target.itemHashes().contains(entry.getKey())) continue;
			if (previousParticleHashes.contains(entry.getKey())
					|| retiredParticleTextureTicks.containsKey(entry.getKey())) {
				retiredParticleTextureTicks.putIfAbsent(entry.getKey(),
						DynamicParticleDefinition.MAX_LIFETIME_TICKS + 2);
				continue;
			}
			safeRelease(client, entry.getValue());
			opaquePixels.remove(entry.getKey());
			loadedIterator.remove();
		}
		var armorIterator = loadedArmorTextures.entrySet().iterator();
		while (armorIterator.hasNext()) {
			var entry = armorIterator.next();
			if (target.armorHashes().contains(entry.getKey())) continue;
			for (Identifier texture : entry.getValue()) safeRelease(client, texture);
			armorIterator.remove();
		}
	}

	private static void safeRelease(Minecraft client, Identifier texture) {
		try {
			client.getTextureManager().release(texture);
		} catch (Throwable error) {
			LittleChemistry.LOGGER.warn("Could not release Little Chemistry runtime texture {}", texture, error);
		}
	}

	public static void accept(Minecraft client, UUID serverId, String hash, byte[] bytes) throws IOException {
		if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
			return;
		}
		if (!hash.equals(DynamicTextureAsset.sha256(bytes))) {
			throw new IOException("Runtime texture failed its SHA-256 check");
		}
		decode(client, serverId, hash, bytes);
		CompletableFuture.runAsync(() -> {
			try {
				Path assetDirectory = assetDirectory(serverId);
				cache(assetDirectory, hash, bytes);
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((ignored, error) -> client.execute(() -> {
			if (error != null) {
				LittleChemistry.LOGGER.warn("Could not cache Little Chemistry texture {} on disk", hash, error);
			}
		}));
	}

	public static boolean isItemTextureReady(String hash) {
		return loadedTextures.containsKey(hash);
	}

	public static boolean areTexturesReady(java.util.Collection<String> hashes) {
		return hashes.stream().allMatch(loadedTextures::containsKey);
	}

	public static Identifier texture(String hash) {
		return loadedTextures.getOrDefault(hash, MissingTextureAtlasSprite.getLocation());
	}

	public static boolean[] opaquePixels(String hash) {
		return opaquePixels.get(hash);
	}

	public static void tick(Minecraft client) {
		var iterator = retiredParticleTextureTicks.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (expectedItemHashes.contains(entry.getKey())) {
				iterator.remove();
				continue;
			}
			int remaining = entry.getValue() - 1;
			if (remaining > 0) {
				entry.setValue(remaining);
				continue;
			}
				Identifier texture = loadedTextures.remove(entry.getKey());
				if (texture != null) safeRelease(client, texture);
			opaquePixels.remove(entry.getKey());
			iterator.remove();
		}
	}

	public static void clear(Minecraft client) {
		if (!client.isSameThread()) {
			client.execute(() -> clear(client));
			return;
		}
		DynamicParticleTextures.clear();
		DynamicArmorAssets.clear();
		for (Identifier texture : loadedTextures.values()) safeRelease(client, texture);
		for (List<Identifier> textures : loadedArmorTextures.values()) {
			for (Identifier texture : textures) safeRelease(client, texture);
		}
		loadedTextures.clear();
		loadedArmorTextures.clear();
		opaquePixels.clear();
		loadingTextures.clear();
		retiredParticleTextureTicks.clear();
		expectedHashes = Set.of();
		expectedItemHashes = Set.of();
		expectedExtrudedItemHashes = Set.of();
		expectedArmorHashes = Set.of();
		expectedParticleHashes = Set.of();
		activeServer = null;
	}

	private static CompletableFuture<Void> decode(Minecraft client, UUID serverId, String hash, byte[] bytes) {
		if (!client.isSameThread()) {
			CompletableFuture<Void> scheduled = new CompletableFuture<>();
			client.execute(() -> decode(client, serverId, hash, bytes).whenComplete((ignored, error) -> {
				if (error != null) scheduled.completeExceptionally(error);
				else scheduled.complete(null);
			}));
			return scheduled;
		}
		if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
			return CompletableFuture.completedFuture(null);
		}
		boolean item = expectedItemHashes.contains(hash);
		boolean extrudedItem = expectedExtrudedItemHashes.contains(hash);
		boolean armor = expectedArmorHashes.contains(hash);
		if ((!item || loadedTextures.containsKey(hash)
				&& (!extrudedItem || opaquePixels.get(hash) != null))
				&& (!armor || loadedArmorTextures.containsKey(hash))) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Void> existing = loadingTextures.get(hash);
		if (existing != null) return existing;
		CompletableFuture<Void> loaded = new CompletableFuture<>();
		loadingTextures.put(hash, loaded);
		Roles roles = new Roles(item && !loadedTextures.containsKey(hash),
				extrudedItem && opaquePixels.get(hash) == null,
				armor && !loadedArmorTextures.containsKey(hash));
		CompletableFuture.supplyAsync(() -> {
			try {
				return decodePrepared(bytes, roles);
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((decoded, error) -> client.execute(() -> {
			loadingTextures.remove(hash, loaded);
				if (error != null) {
					LittleChemistry.LOGGER.error("Could not decode Little Chemistry texture {}", hash, error);
					loaded.completeExceptionally(error);
				return;
			}
			if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
				decoded.close();
				loaded.complete(null);
				return;
			}
			if (item != expectedItemHashes.contains(hash)
					|| extrudedItem != expectedExtrudedItemHashes.contains(hash)
					|| armor != expectedArmorHashes.contains(hash)) {
				decoded.close();
				decode(client, serverId, hash, bytes).whenComplete((ignored, retryError) -> {
					if (retryError != null) loaded.completeExceptionally(retryError);
					else loaded.complete(null);
				});
				return;
			}

			List<Identifier> registered = new ArrayList<>();
			try {
				Identifier itemId = null;
				List<Identifier> armorIds = null;
				if (decoded.image() != null && !loadedTextures.containsKey(hash)) {
					itemId = LittleChemistry.id("runtime/" + hash);
					register(client, itemId, "Little Chemistry " + hash, decoded.takeImage(), registered);
				}
				if (decoded.humanoid() != null && !loadedArmorTextures.containsKey(hash)) {
					armorIds = armorTextureIds(hash);
					register(client, armorIds.get(0), "Little Chemistry armor " + hash,
							decoded.takeHumanoid(), registered);
					register(client, armorIds.get(1), "Little Chemistry armor leggings " + hash,
							decoded.takeLeggings(), registered);
					register(client, armorIds.get(2), "Little Chemistry baby armor " + hash,
							decoded.takeBaby(), registered);
				}
				if (itemId != null) loadedTextures.put(hash, itemId);
				if (decoded.opaquePixels() != null && opaquePixels.get(hash) == null) {
					opaquePixels.put(hash, decoded.opaquePixels());
				}
				if (armorIds != null) loadedArmorTextures.put(hash, armorIds);
				decoded.close();
				loaded.complete(null);
			} catch (Throwable registrationError) {
				for (Identifier texture : registered) safeRelease(client, texture);
				decoded.close();
				LittleChemistry.LOGGER.error("Could not register Little Chemistry texture {}", hash, registrationError);
				loaded.completeExceptionally(registrationError);
			}
		}));
		return loaded;
	}

	private static boolean[] opacity(NativeImage image) {
		boolean[] opacity = new boolean[DynamicTextureAsset.WIDTH * DynamicTextureAsset.HEIGHT];
		for (int y = 0; y < DynamicTextureAsset.HEIGHT; y++) {
			for (int x = 0; x < DynamicTextureAsset.WIDTH; x++) {
				opacity[y * DynamicTextureAsset.WIDTH + x] = ((image.getPixel(x, y) >>> 24) & 0xFF) != 0;
			}
		}
		return opacity;
	}

	private static NativeImage copyTexture(NativeImage source) {
		NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), true);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) result.setPixel(x, y, source.getPixel(x, y));
		}
		return result;
	}

	/**
	 * Minecraft 26.2 gives baby humanoids a separate 64x64 model and UV layout. Generation only has
	 * to author the standard 64x32 sheet; remap each adult cuboid face to the corresponding baby
	 * cuboid so the same artwork remains meaningful instead of stretching or tiling the item icon.
	 */
	private static NativeImage babyArmorTexture(NativeImage adult) {
		NativeImage baby = new NativeImage(64, 64, true);
		copyCuboidUv(adult, baby, 0, 0, 8, 8, 8, 0, 0, 9, 8, 8);          // head
		copyCuboidUv(adult, baby, 16, 16, 8, 12, 4, 0, 17, 6, 5, 3);      // torso
		copyCuboidUv(adult, baby, 16, 16, 8, 12, 4, 0, 36, 6, 2, 3);      // leggings waist
		copyCuboidUv(adult, baby, 40, 16, 4, 12, 4, 30, 25, 2, 5, 3);     // right arm
		copyMirroredCuboidUv(adult, baby, 40, 16, 4, 12, 4, 30, 17, 2, 5, 3); // left arm
		copyCuboidUv(adult, baby, 0, 16, 4, 12, 4, 18, 17, 3, 4, 3);      // right leg
		copyMirroredCuboidUv(adult, baby, 0, 16, 4, 12, 4, 18, 24, 3, 4, 3); // left leg
		copyFootUv(adult, baby, 0, 16, 4, 12, 4, 0, 25, 3, 1, 3, true);   // left foot
		copyFootUv(adult, baby, 0, 16, 4, 12, 4, 0, 29, 3, 1, 3, true);   // right foot
		return baby;
	}

	private static void copyCuboidUv(NativeImage source, NativeImage destination,
			int sourceU, int sourceV, int sourceWidth, int sourceHeight, int sourceDepth,
			int destinationU, int destinationV, int destinationWidth, int destinationHeight, int destinationDepth) {
		copyRegion(source, destination,
				sourceU + sourceDepth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth, destinationV, destinationWidth, destinationDepth);
		copyRegion(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth + destinationWidth, destinationV, destinationWidth, destinationDepth);
		copyRegion(source, destination,
				sourceU, sourceV + sourceDepth, sourceDepth, sourceHeight,
				destinationU, destinationV + destinationDepth, destinationDepth, destinationHeight);
		copyRegion(source, destination,
				sourceU + sourceDepth, sourceV + sourceDepth, sourceWidth, sourceHeight,
				destinationU + destinationDepth, destinationV + destinationDepth, destinationWidth, destinationHeight);
		copyRegion(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceV + sourceDepth, sourceDepth, sourceHeight,
				destinationU + destinationDepth + destinationWidth, destinationV + destinationDepth,
				destinationDepth, destinationHeight);
		copyRegion(source, destination,
				sourceU + sourceDepth * 2 + sourceWidth, sourceV + sourceDepth, sourceWidth, sourceHeight,
				destinationU + destinationDepth * 2 + destinationWidth, destinationV + destinationDepth,
				destinationWidth, destinationHeight);
	}

	private static void copyMirroredCuboidUv(NativeImage source, NativeImage destination,
			int sourceU, int sourceV, int sourceWidth, int sourceHeight, int sourceDepth,
			int destinationU, int destinationV, int destinationWidth, int destinationHeight, int destinationDepth) {
		copyRegionFlippedX(source, destination,
				sourceU + sourceDepth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth, destinationV, destinationWidth, destinationDepth);
		copyRegionFlippedX(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth + destinationWidth, destinationV, destinationWidth, destinationDepth);
		copyRegionFlippedX(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceV + sourceDepth, sourceDepth, sourceHeight,
				destinationU, destinationV + destinationDepth, destinationDepth, destinationHeight);
		copyRegionFlippedX(source, destination,
				sourceU + sourceDepth, sourceV + sourceDepth, sourceWidth, sourceHeight,
				destinationU + destinationDepth, destinationV + destinationDepth, destinationWidth, destinationHeight);
		copyRegionFlippedX(source, destination,
				sourceU, sourceV + sourceDepth, sourceDepth, sourceHeight,
				destinationU + destinationDepth + destinationWidth, destinationV + destinationDepth,
				destinationDepth, destinationHeight);
		copyRegionFlippedX(source, destination,
				sourceU + sourceDepth * 2 + sourceWidth, sourceV + sourceDepth, sourceWidth, sourceHeight,
				destinationU + destinationDepth * 2 + destinationWidth, destinationV + destinationDepth,
				destinationWidth, destinationHeight);
	}

	private static void copyFootUv(NativeImage source, NativeImage destination,
			int sourceU, int sourceV, int sourceWidth, int sourceHeight, int sourceDepth,
			int destinationU, int destinationV, int destinationWidth, int destinationHeight, int destinationDepth,
			boolean mirrored) {
		RegionCopier copier = mirrored
				? RuntimeTextureStore::copyRegionFlippedX
				: RuntimeTextureStore::copyRegion;
		copier.copy(source, destination,
				sourceU + sourceDepth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth, destinationV, destinationWidth, destinationDepth);
		copier.copy(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceV, sourceWidth, sourceDepth,
				destinationU + destinationDepth + destinationWidth, destinationV, destinationWidth, destinationDepth);
		int sourceSideY = sourceV + sourceDepth + sourceHeight - 1;
		// A baby foot is only one pixel tall; sample the bottom of the adult leg where boot artwork lives.
		copier.copy(source, destination,
				sourceU + sourceDepth + sourceWidth, sourceSideY, sourceDepth, 1,
				destinationU, destinationV + destinationDepth, destinationDepth, destinationHeight);
		copier.copy(source, destination,
				sourceU + sourceDepth, sourceSideY, sourceWidth, 1,
				destinationU + destinationDepth, destinationV + destinationDepth, destinationWidth, destinationHeight);
		copier.copy(source, destination,
				sourceU, sourceSideY, sourceDepth, 1,
				destinationU + destinationDepth + destinationWidth, destinationV + destinationDepth,
				destinationDepth, destinationHeight);
		copier.copy(source, destination,
				sourceU + sourceDepth * 2 + sourceWidth, sourceSideY, sourceWidth, 1,
				destinationU + destinationDepth * 2 + destinationWidth, destinationV + destinationDepth,
				destinationWidth, destinationHeight);
	}

	private static void copyRegion(NativeImage source, NativeImage destination,
			int sourceX, int sourceY, int sourceWidth, int sourceHeight,
			int destinationX, int destinationY, int destinationWidth, int destinationHeight) {
		for (int y = 0; y < destinationHeight; y++) {
			for (int x = 0; x < destinationWidth; x++) {
				int sampledX = sourceX + Math.min(sourceWidth - 1, x * sourceWidth / destinationWidth);
				int sampledY = sourceY + Math.min(sourceHeight - 1, y * sourceHeight / destinationHeight);
				destination.setPixel(destinationX + x, destinationY + y, source.getPixel(sampledX, sampledY));
			}
		}
	}

	private static void copyRegionFlippedX(NativeImage source, NativeImage destination,
			int sourceX, int sourceY, int sourceWidth, int sourceHeight,
			int destinationX, int destinationY, int destinationWidth, int destinationHeight) {
		for (int y = 0; y < destinationHeight; y++) {
			for (int x = 0; x < destinationWidth; x++) {
				int sampledX = sourceX + sourceWidth - 1
						- Math.min(sourceWidth - 1, x * sourceWidth / destinationWidth);
				int sampledY = sourceY + Math.min(sourceHeight - 1, y * sourceHeight / destinationHeight);
				destination.setPixel(destinationX + x, destinationY + y, source.getPixel(sampledX, sampledY));
			}
		}
	}

	@FunctionalInterface
	private interface RegionCopier {
		void copy(NativeImage source, NativeImage destination,
				int sourceX, int sourceY, int sourceWidth, int sourceHeight,
				int destinationX, int destinationY, int destinationWidth, int destinationHeight);
	}

	private static NativeImage legacyArmorTexture(NativeImage source, int width, int height) {
		NativeImage result = new NativeImage(width, height, true);
		Map<Integer, Integer> opaqueCounts = new HashMap<>();
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int color = source.getPixel(x, y);
				if (((color >>> 24) & 0xFF) != 0) opaqueCounts.merge(color, 1, Integer::sum);
			}
		}
		int fallback = opaqueCounts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(0xFF7F7F7F);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int color = source.getPixel(x % source.getWidth(), y % source.getHeight());
				result.setPixel(x, y, ((color >>> 24) & 0xFF) == 0 ? fallback : color);
			}
		}
		return result;
	}

	private static Path assetDirectory(UUID serverId) {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("little-chemistry")
				.resolve("cache")
				.resolve(serverId.toString())
				.resolve("assets");
	}

	private static byte[] reconstruct(List<DynamicContentDefinition> definitions, String hash) throws IOException {
		for (DynamicContentDefinition definition : definitions) {
			if (hash.equals(definition.textureHash())) {
				return definition.texture() == null
						? DynamicTextureAsset.generate(definition.textureSeed())
							: definition.texture().renderPng();
			}
			if (definition.blockModel() != null) {
				for (var texture : definition.blockModel().textures()) {
					if (hash.equals(texture.hash())) return texture.texture().renderPng();
				}
			}
			if (hash.equals(definition.armorDisplayTextureHash()) && definition.armorDisplayTexture() != null) {
				return definition.armorDisplayTexture().renderPng();
			}
			for (var particle : definition.customParticles()) {
				for (var frame : particle.frames()) {
					if (hash.equals(frame.textureHash())) return frame.texture().renderPng();
				}
			}
		}
		return null;
	}

	private static void cache(Path directory, String hash, byte[] bytes) throws IOException {
		Files.createDirectories(directory);
		Path destination = directory.resolve(hash + ".png");
		Path temporary = Files.createTempFile(directory, hash + ".", ".tmp");
		try {
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private record Target(
			Set<String> expectedHashes,
			Set<String> itemHashes,
			Set<String> extrudedItemHashes,
			Set<String> armorHashes,
			Set<String> particleHashes
	) {
	}

	private record Roles(boolean loadItem, boolean loadOpacity, boolean loadArmor) {
	}

	public static final class Preparation implements AutoCloseable {
		private final UUID serverId;
		private final Target target;
		private final Map<String, DecodedTexture> decodedAssets;
		private final List<String> missing;
		private boolean claimed;
		private boolean closed;

		private Preparation(UUID serverId, Target target, Map<String, DecodedTexture> decodedAssets,
				List<String> missing) {
			this.serverId = serverId;
			this.target = target;
			this.decodedAssets = decodedAssets;
			this.missing = missing;
		}

		private UUID serverId() {
			return serverId;
		}

		private Target target() {
			return target;
		}

		private Map<String, DecodedTexture> decodedAssets() {
			return decodedAssets;
		}

		public List<String> missing() {
			return missing;
		}

		private void claim() {
			if (claimed || closed) throw new IllegalStateException("Runtime texture preparation was already consumed");
			claimed = true;
		}

		@Override
		public void close() {
			if (claimed) throw new IllegalStateException("A claimed runtime texture preparation cannot be closed externally");
			closeImages();
		}

		private void closeAfterClaim() {
			closeImages();
		}

		private void closeImages() {
			if (closed) return;
			closed = true;
			for (DecodedTexture decoded : decodedAssets.values()) decoded.close();
		}
	}

	private static final class DecodedTexture {
		private NativeImage image;
		private final boolean[] opaquePixels;
		private NativeImage humanoid;
		private NativeImage leggings;
		private NativeImage baby;

		private DecodedTexture(NativeImage image, boolean[] opaquePixels, NativeImage humanoid,
				NativeImage leggings, NativeImage baby) {
			this.image = image;
			this.opaquePixels = opaquePixels;
			this.humanoid = humanoid;
			this.leggings = leggings;
			this.baby = baby;
		}

		private NativeImage image() {
			return image;
		}

		private boolean[] opaquePixels() {
			return opaquePixels;
		}

		private NativeImage humanoid() {
			return humanoid;
		}

		private NativeImage takeImage() {
			NativeImage result = image;
			image = null;
			return result;
		}

		private NativeImage takeHumanoid() {
			NativeImage result = humanoid;
			humanoid = null;
			return result;
		}

		private NativeImage takeLeggings() {
			NativeImage result = leggings;
			leggings = null;
			return result;
		}

		private NativeImage takeBaby() {
			NativeImage result = baby;
			baby = null;
			return result;
		}

		private void close() {
			if (image != null) image.close();
			if (humanoid != null) humanoid.close();
			if (leggings != null) leggings.close();
			if (baby != null) baby.close();
			image = null;
			humanoid = null;
			leggings = null;
			baby = null;
		}
	}
}
