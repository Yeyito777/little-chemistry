package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentJson;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicEntityObjects;
import com.yeyito.littlechemistry.mixin.CreativeModeTabsAccessor;
import com.yeyito.littlechemistry.network.DynamicAssetPayload;
import com.yeyito.littlechemistry.network.DynamicAssetRequestPayload;
import com.yeyito.littlechemistry.network.DynamicContentPayload;
import com.yeyito.littlechemistry.network.OpenCreationScreenPayload;
import com.yeyito.littlechemistry.network.OpenDeletionScreenPayload;
import com.yeyito.littlechemistry.particle.DynamicParticleRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class LittleChemistryClient implements ClientModInitializer {
	private static UUID activeServer;
	private static long activeRevision = -1;

	@Override
	public void onInitializeClient() {
		DynamicActionKeys.register();
		MenuScreens.register(com.yeyito.littlechemistry.crafting.DynamicWorkstationMenu.TYPE,
				DynamicWorkstationScreen::new);
		ParticleProviderRegistry.getInstance().register(DynamicParticleRegistry.TYPE,
				(options, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
						new RuntimeCustomParticle(level, options, x, y, z, velocityX, velocityY, velocityZ));
		ClientTickEvents.END_CLIENT_TICK.register(RuntimeTextureStore::tick);
		ClientTickEvents.END_CLIENT_TICK.register(DynamicActionKeys::tick);
		SpecialModelRenderers.ID_MAPPER.put(
				LittleChemistry.id("dynamic"),
				DynamicSpecialItemRenderer.Unbaked.MAP_CODEC
		);
		BlockEntityRenderers.register(
				DynamicContentObjects.BLOCK_ENTITY_TYPE,
				context -> new DynamicBlockEntityRenderer()
		);
		EntityRendererRegistry.register(DynamicEntityObjects.GROUND_CREATURE, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.GROUND_MONSTER, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.FLYING_CREATURE, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.FLYING_MONSTER, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.AQUATIC_CREATURE, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.AQUATIC_MONSTER, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.AMPHIBIOUS_CREATURE, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.AMPHIBIOUS_MONSTER, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.VEHICLE_CREATURE, DynamicEntityRenderer::new);
		EntityRendererRegistry.register(DynamicEntityObjects.VEHICLE_MONSTER, DynamicEntityRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(DynamicContentPayload.TYPE,
				(payload, context) -> apply(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(DynamicAssetPayload.TYPE,
					(payload, context) -> applyAsset(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenCreationScreenPayload.TYPE,
						(payload, context) -> context.client().gui.setScreen(new WandCreationScreen()));
		ClientPlayNetworking.registerGlobalReceiver(OpenDeletionScreenPayload.TYPE,
						(payload, context) -> context.client().gui.setScreen(new WandDeletionScreen()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
				DynamicContentCatalog.clear();
				RuntimeTextureStore.clear(client);
				activeServer = null;
					activeRevision = -1;
					DynamicActionKeys.reset();
				});
	}

	private static void apply(Minecraft client, DynamicContentPayload payload) {
		try {
			DynamicContentJson.Decoded decoded = DynamicContentJson.decode(payload.definitionsJson());
			if (!decoded.serverId().equals(payload.serverId()) || decoded.revision() != payload.revision()) {
				throw new IOException("The synchronized definitions do not match their packet metadata");
			}
			if (payload.serverId().equals(activeServer) && payload.revision() < activeRevision) {
				LittleChemistry.LOGGER.debug("Ignoring stale Little Chemistry catalog revision {} behind {}",
						payload.revision(), activeRevision);
				return;
			}

			Path directory = FabricLoader.getInstance().getConfigDir()
					.resolve("little-chemistry")
					.resolve("cache")
					.resolve(payload.serverId().toString());
			Files.createDirectories(directory);
			Path definitions = directory.resolve("definitions.json");
			Path temporary = definitions.resolveSibling("definitions.json.tmp");
			Files.write(temporary, payload.definitionsJson());
			Files.move(temporary, definitions, StandardCopyOption.REPLACE_EXISTING);

			activeServer = payload.serverId();
			activeRevision = payload.revision();

			RuntimeTextureStore.prepare(client, payload.serverId(), decoded.definitions())
					.whenComplete((preparation, error) -> client.execute(() -> {
						if (!payload.serverId().equals(activeServer) || payload.revision() != activeRevision) {
							if (preparation != null) preparation.close();
							return;
						}
						if (error != null) {
							LittleChemistry.LOGGER.error("Could not prepare the Little Chemistry asset cache", error);
							return;
						}
						java.util.List<String> missing;
						try {
							missing = RuntimeTextureStore.commit(client, payload.serverId(), preparation, () -> {
								DynamicContentCatalog.replace(decoded.definitions());
								if (client.level != null) {
									for (var entity : client.level.entitiesForRendering()) {
										if (entity instanceof com.yeyito.littlechemistry.content.DynamicCarrierEntity dynamic) {
											dynamic.refreshDimensions();
										}
									}
								}
							});
						} catch (Throwable commitError) {
							LittleChemistry.LOGGER.error("Could not commit the Little Chemistry catalog and textures",
									commitError);
							return;
						}
						refreshCreativeTabs(client);
						refreshRecipeBook(client);
						for (int start = 0; start < missing.size(); start += DynamicAssetRequestPayload.MAX_HASHES) {
							int end = Math.min(missing.size(), start + DynamicAssetRequestPayload.MAX_HASHES);
							ClientPlayNetworking.send(new DynamicAssetRequestPayload(missing.subList(start, end)));
						}
						LittleChemistry.LOGGER.info("Little Chemistry catalog revision {} is ready; requesting {} assets",
								payload.revision(), missing.size());
						LittleChemistry.LOGGER.info("Applied Little Chemistry catalog revision {} with {} entries",
								payload.revision(), decoded.definitions().size());
					}));
		} catch (Exception error) {
			LittleChemistry.LOGGER.error("Could not apply synchronized Little Chemistry content", error);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("[Little Chemistry] Could not install server content: "
						+ safeMessage(error)).withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void applyAsset(Minecraft client, DynamicAssetPayload payload) {
		if (activeServer == null) {
			return;
		}
		try {
			RuntimeTextureStore.accept(client, activeServer, payload.hash(), payload.pngBytes());
		} catch (IOException error) {
			LittleChemistry.LOGGER.error("Could not cache Little Chemistry runtime asset {}", payload.hash(), error);
		}
	}

	private static void refreshCreativeTabs(Minecraft client) {
		CreativeModeTabsAccessor.littleChemistry$setCachedParameters(null);
		if (client.level != null && client.player != null) {
			net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
					client.level.enabledFeatures(),
					client.player.canUseGameMasterBlocks(),
					client.level.registryAccess()
			);
		}
	}

	private static void refreshRecipeBook(Minecraft client) {
		if (client.player == null || client.level == null || client.getConnection() == null) return;
		ClientRecipeBook recipeBook = client.player.getRecipeBook();
		recipeBook.rebuildCollections();
		client.getConnection().searchTrees().updateRecipes(recipeBook, client.level);
		if (client.gui.screen() instanceof RecipeUpdateListener listener) listener.recipesUpdated();
	}

	private static String safeMessage(Exception error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.replaceAll("[\\r\\n]+", " ");
	}
}
