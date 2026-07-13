package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
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
	private static final Set<String> loadingTextures = new HashSet<>();
	private static Set<String> expectedHashes = Set.of();
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
		expectedHashes = definitions.stream().map(DynamicContentDefinition::textureHash).collect(java.util.stream.Collectors.toUnmodifiableSet());
		expectedArmorHashes = definitions.stream()
				.filter(definition -> definition.type() == DynamicContentType.ARMOR)
				.map(DynamicContentDefinition::textureHash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		var loadedIterator = loadedTextures.entrySet().iterator();
		while (loadedIterator.hasNext()) {
			var entry = loadedIterator.next();
			if (expectedHashes.contains(entry.getKey())) continue;
			client.getTextureManager().release(entry.getValue());
			for (Identifier armorTexture : loadedArmorTextures.getOrDefault(entry.getKey(), List.of())) {
				client.getTextureManager().release(armorTexture);
			}
			loadedArmorTextures.remove(entry.getKey());
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
		List<String> hashesToCheck = new ArrayList<>();
		for (String hash : expectedHashes) {
			boolean armorReady = !expectedArmorHashes.contains(hash) || loadedArmorTextures.containsKey(hash);
			if ((loadedTextures.containsKey(hash) && armorReady) || loadingTextures.contains(hash)) {
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
				prepared.cachedAssets().forEach((hash, bytes) -> decode(client, serverId, hash, bytes));
				result.complete(prepared.missing());
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
		CompletableFuture.runAsync(() -> {
			try {
				Path assetDirectory = assetDirectory(serverId);
				Files.createDirectories(assetDirectory);
				Path destination = assetDirectory.resolve(hash + ".png");
				Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
				Files.write(temporary, bytes);
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((ignored, error) -> client.execute(() -> {
			if (error != null) {
				LittleChemistry.LOGGER.warn("Could not cache Little Chemistry texture {} on disk", hash, error);
			}
			decode(client, serverId, hash, bytes);
		}));
	}

	public static Identifier texture(String hash) {
		return loadedTextures.getOrDefault(hash, TextureManager.INTENTIONAL_MISSING_TEXTURE);
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
		expectedArmorHashes = Set.of();
		activeServer = null;
	}

	private static void decode(Minecraft client, UUID serverId, String hash, byte[] bytes) {
		if (!loadingTextures.add(hash)) {
			return;
		}
		boolean armor = expectedArmorHashes.contains(hash);
		CompletableFuture.supplyAsync(() -> {
			try {
				NativeImage image = NativeImage.read(bytes);
				if (image.getWidth() != DynamicTextureAsset.WIDTH || image.getHeight() != DynamicTextureAsset.HEIGHT) {
					image.close();
					throw new IOException("Runtime texture must be 16x16 pixels");
				}
				boolean[] opacity = new boolean[DynamicTextureAsset.WIDTH * DynamicTextureAsset.HEIGHT];
				for (int y = 0; y < DynamicTextureAsset.HEIGHT; y++) {
					for (int x = 0; x < DynamicTextureAsset.WIDTH; x++) {
						opacity[y * DynamicTextureAsset.WIDTH + x] = ((image.getPixel(x, y) >>> 24) & 0xFF) != 0;
					}
				}
				return new DecodedTexture(
						image,
						opacity,
						armor ? armorTexture(image, 64, 32) : null,
						armor ? armorTexture(image, 64, 32) : null,
						armor ? armorTexture(image, 64, 64) : null);
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((decoded, error) -> client.execute(() -> {
			loadingTextures.remove(hash);
			if (error != null) {
				LittleChemistry.LOGGER.error("Could not decode Little Chemistry texture {}", hash, error);
				return;
			}
			if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
				decoded.close();
				return;
			}
			if (armor != expectedArmorHashes.contains(hash)) {
				decoded.close();
				decode(client, serverId, hash, bytes);
				return;
			}

			Identifier textureId = LittleChemistry.id("runtime/" + hash);
			client.getTextureManager().register(textureId, new DynamicTexture(
					() -> "Little Chemistry " + hash,
					decoded.image()
			));
			loadedTextures.put(hash, textureId);
			opaquePixels.put(hash, decoded.opaquePixels());

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
		}));
	}

	private static NativeImage armorTexture(NativeImage source, int width, int height) {
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
			image.close();
			if (humanoid != null) humanoid.close();
			if (leggings != null) leggings.close();
			if (baby != null) baby.close();
		}
	}
}
