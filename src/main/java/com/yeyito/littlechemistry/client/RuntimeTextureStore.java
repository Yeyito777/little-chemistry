package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
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
	private static final Set<String> loadingTextures = new HashSet<>();
	private static Set<String> expectedHashes = Set.of();
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
		expectedHashes = definitions.stream().map(DynamicContentDefinition::textureHash).collect(java.util.stream.Collectors.toUnmodifiableSet());
		Path assetDirectory = assetDirectory(serverId);
		Files.createDirectories(assetDirectory);
		List<String> hashesToCheck = new ArrayList<>();
		for (String hash : expectedHashes) {
			if (loadedTextures.containsKey(hash) || loadingTextures.contains(hash)) {
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

	public static void clear(Minecraft client) {
		for (Identifier texture : loadedTextures.values()) {
			client.getTextureManager().release(texture);
		}
		loadedTextures.clear();
		loadingTextures.clear();
		expectedHashes = Set.of();
		activeServer = null;
	}

	private static void decode(Minecraft client, UUID serverId, String hash, byte[] bytes) {
		if (!loadingTextures.add(hash)) {
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				NativeImage image = NativeImage.read(bytes);
				if (image.getWidth() != DynamicTextureAsset.WIDTH || image.getHeight() != DynamicTextureAsset.HEIGHT) {
					image.close();
					throw new IOException("Runtime texture must be 16x16 pixels");
				}
				return image;
			} catch (IOException error) {
				throw new RuntimeException(error);
			}
		}, DECODER).whenComplete((image, error) -> client.execute(() -> {
			loadingTextures.remove(hash);
			if (error != null) {
				LittleChemistry.LOGGER.error("Could not decode Little Chemistry texture {}", hash, error);
				return;
			}
			if (!serverId.equals(activeServer) || !expectedHashes.contains(hash)) {
				image.close();
				return;
			}
			Identifier textureId = LittleChemistry.id("runtime/" + hash);
			client.getTextureManager().register(textureId, new DynamicTexture(
					() -> "Little Chemistry " + hash,
					image
			));
			loadedTextures.put(hash, textureId);
	}));
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
}
