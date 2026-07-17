package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
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
	private static Set<String> expectedHashes = Set.of();
	private static Set<String> expectedItemHashes = Set.of();
	private static Set<String> expectedArmorHashes = Set.of();
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

	public static CompletableFuture<List<String>> prepare(Minecraft client, UUID serverId,
			List<DynamicContentDefinition> definitions) throws IOException {
		beginServer(client, serverId);
		DynamicParticleTextures.clear();
		expectedItemHashes = definitions.stream().map(DynamicContentDefinition::textureHash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		expectedArmorHashes = definitions.stream()
				.filter(definition -> definition.type() == DynamicContentType.ARMOR)
				.map(DynamicContentDefinition::effectiveArmorDisplayTextureHash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		Set<String> allExpectedHashes = new HashSet<>(expectedItemHashes);
		allExpectedHashes.addAll(expectedArmorHashes);
		expectedHashes = Set.copyOf(allExpectedHashes);
		var loadedIterator = loadedTextures.entrySet().iterator();
		while (loadedIterator.hasNext()) {
			var entry = loadedIterator.next();
			if (expectedItemHashes.contains(entry.getKey())) continue;
			client.getTextureManager().release(entry.getValue());
			opaquePixels.remove(entry.getKey());
			loadedIterator.remove();
		}
		var armorIterator = loadedArmorTextures.entrySet().iterator();
		while (armorIterator.hasNext()) {
			var entry = armorIterator.next();
			if (expectedArmorHashes.contains(entry.getKey())) continue;
			for (Identifier texture : entry.getValue()) client.getTextureManager().release(texture);
			armorIterator.remove();
		}
		Path assetDirectory = assetDirectory(serverId);
		Files.createDirectories(assetDirectory);
		List<DynamicContentDefinition> definitionSnapshot = List.copyOf(definitions);
		List<String> hashesToCheck = new ArrayList<>();
		for (String hash : expectedHashes) {
			boolean itemReady = !expectedItemHashes.contains(hash) || loadedTextures.containsKey(hash);
			boolean armorReady = !expectedArmorHashes.contains(hash) || loadedArmorTextures.containsKey(hash);
			if ((itemReady && armorReady) || loadingTextures.containsKey(hash)) {
				continue;
			}
			hashesToCheck.add(hash);
		}

		return CompletableFuture.supplyAsync(() -> {
			Map<String, byte[]> cachedAssets = new HashMap<>();
			List<String> missing = new ArrayList<>();
			for (String hash : hashesToCheck) {
				Path cached = assetDirectory.resolve(hash + ".png");
				try {
					if (Files.isRegularFile(cached)) {
						byte[] bytes = Files.readAllBytes(cached);
						if (hash.equals(DynamicTextureAsset.sha256(bytes))) {
							cachedAssets.put(hash, bytes);
							continue;
						}
						Files.deleteIfExists(cached);
					}
				} catch (IOException error) {
					LittleChemistry.LOGGER.warn("Could not read cached Little Chemistry texture {}", hash, error);
				}
				try {
					byte[] reconstructed = reconstruct(definitionSnapshot, hash);
					if (reconstructed != null && hash.equals(DynamicTextureAsset.sha256(reconstructed))) {
						cachedAssets.put(hash, reconstructed);
						try {
							cache(assetDirectory, hash, reconstructed);
						} catch (IOException error) {
							LittleChemistry.LOGGER.warn("Could not cache reconstructed Little Chemistry texture {}", hash, error);
						}
						continue;
					}
				} catch (IOException error) {
					LittleChemistry.LOGGER.warn("Could not reconstruct Little Chemistry texture {} from its definition", hash, error);
				}
				missing.add(hash);
			}
			return new Prepared(cachedAssets, missing);
		}, DECODER).thenCompose(prepared -> {
			CompletableFuture<List<String>> result = new CompletableFuture<>();
			client.execute(() -> {
				if (!serverId.equals(activeServer)) {
					result.complete(List.of());
					return;
				}
				CompletableFuture<?>[] decodes = prepared.cachedAssets().entrySet().stream()
						.map(entry -> decode(client, serverId, entry.getKey(), entry.getValue()))
						.toArray(CompletableFuture[]::new);
				CompletableFuture.allOf(decodes).whenComplete((ignored, error) -> {
					if (error != null) result.completeExceptionally(error);
					else result.complete(prepared.missing());
				});
			});
			return result;
		});
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

	public static Identifier texture(String hash) {
		return loadedTextures.getOrDefault(hash, MissingTextureAtlasSprite.getLocation());
	}

	public static boolean[] opaquePixels(String hash) {
		return opaquePixels.get(hash);
	}

	public static void clear(Minecraft client) {
		if (!client.isSameThread()) {
			client.execute(() -> clear(client));
			return;
		}
		DynamicParticleTextures.clear();
		DynamicArmorAssets.clear();
		for (Identifier texture : loadedTextures.values()) {
			client.getTextureManager().release(texture);
		}
		for (List<Identifier> textures : loadedArmorTextures.values()) {
			for (Identifier texture : textures) client.getTextureManager().release(texture);
		}
		loadedTextures.clear();
		loadedArmorTextures.clear();
		opaquePixels.clear();
		loadingTextures.clear();
		expectedHashes = Set.of();
		expectedItemHashes = Set.of();
		expectedArmorHashes = Set.of();
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
		boolean armor = expectedArmorHashes.contains(hash);
		if ((!item || loadedTextures.containsKey(hash))
				&& (!armor || loadedArmorTextures.containsKey(hash))) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Void> existing = loadingTextures.get(hash);
		if (existing != null) return existing;
		CompletableFuture<Void> loaded = new CompletableFuture<>();
		loadingTextures.put(hash, loaded);
		CompletableFuture.supplyAsync(() -> {
			try {
				NativeImage source = NativeImage.read(bytes);
				if (item && (source.getWidth() != DynamicTextureAsset.WIDTH
						|| source.getHeight() != DynamicTextureAsset.HEIGHT)) {
					source.close();
					throw new IOException("Runtime inventory texture must be 16x16 pixels");
				}
				boolean[] opacity = item ? opacity(source) : null;
				NativeImage humanoid = null;
				NativeImage leggings = null;
				NativeImage baby = null;
				if (armor) {
					if (source.getWidth() == DynamicArmorDisplayTextureSpec.WIDTH
							&& source.getHeight() == DynamicArmorDisplayTextureSpec.HEIGHT) {
						humanoid = copyTexture(source);
						leggings = copyTexture(source);
						baby = babyArmorTexture(source);
					} else if (source.getWidth() == DynamicTextureAsset.WIDTH
							&& source.getHeight() == DynamicTextureAsset.HEIGHT) {
						// Format 6 and older used the inventory icon for the worn layer. Keep those worlds loadable.
						humanoid = legacyArmorTexture(source, 64, 32);
						leggings = legacyArmorTexture(source, 64, 32);
						baby = legacyArmorTexture(source, 64, 64);
					} else {
						source.close();
						throw new IOException("Runtime armor display texture must be 64x32 pixels");
					}
				}
				NativeImage itemImage = item ? source : null;
				if (!item) source.close();
				return new DecodedTexture(itemImage, opacity, humanoid, leggings, baby);
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((decoded, error) -> client.execute(() -> {
			loadingTextures.remove(hash, loaded);
			if (error != null) {
				LittleChemistry.LOGGER.error("Could not decode Little Chemistry texture {}", hash, error);
				loaded.complete(null);
				return;
			}
			if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
				decoded.close();
				loaded.complete(null);
				return;
			}
			if (item != expectedItemHashes.contains(hash) || armor != expectedArmorHashes.contains(hash)) {
				decoded.close();
				decode(client, serverId, hash, bytes).whenComplete((ignored, retryError) -> {
					if (retryError != null) loaded.completeExceptionally(retryError);
					else loaded.complete(null);
				});
				return;
			}

			try {
				if (decoded.image() != null) {
					Identifier textureId = LittleChemistry.id("runtime/" + hash);
					client.getTextureManager().register(textureId, new DynamicTexture(
							() -> "Little Chemistry " + hash,
							decoded.image()
					));
					loadedTextures.put(hash, textureId);
					opaquePixels.put(hash, decoded.opaquePixels());
				}

				if (decoded.humanoid() != null) {
					List<Identifier> armorTextureIds = List.of(
							DynamicArmorAssets.textureLocation(hash, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID),
							DynamicArmorAssets.textureLocation(hash, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS),
							DynamicArmorAssets.textureLocation(hash, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_BABY));
					client.getTextureManager().register(armorTextureIds.get(0), new DynamicTexture(
							() -> "Little Chemistry armor " + hash, decoded.humanoid()));
					client.getTextureManager().register(armorTextureIds.get(1), new DynamicTexture(
							() -> "Little Chemistry armor leggings " + hash, decoded.leggings()));
					client.getTextureManager().register(armorTextureIds.get(2), new DynamicTexture(
							() -> "Little Chemistry baby armor " + hash, decoded.baby()));
					loadedArmorTextures.put(hash, armorTextureIds);
				}
				loaded.complete(null);
			} catch (Throwable registrationError) {
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
			if (hash.equals(definition.armorDisplayTextureHash()) && definition.armorDisplayTexture() != null) {
				return definition.armorDisplayTexture().renderPng();
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

	private record Prepared(Map<String, byte[]> cachedAssets, List<String> missing) {
	}

	private record DecodedTexture(
			NativeImage image,
			boolean[] opaquePixels,
			NativeImage humanoid,
			NativeImage leggings,
			NativeImage baby
	) {
		private void close() {
			if (image != null) image.close();
			if (humanoid != null) humanoid.close();
			if (leggings != null) leggings.close();
			if (baby != null) baby.close();
		}
	}
}
