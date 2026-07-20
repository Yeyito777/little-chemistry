package com.yeyito.littlechemistry;

import com.yeyito.littlechemistry.command.LittleChemistryCommands;
import com.yeyito.littlechemistry.ai.generation.ContentGenerationService;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicEntityObjects;
import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.PortableCraftingComponents;
import com.yeyito.littlechemistry.item.CraftingTableOnAStickItem;
import com.yeyito.littlechemistry.item.WandOfCreationItem;
import com.yeyito.littlechemistry.item.WandOfDeletionItem;
import com.yeyito.littlechemistry.network.CreateContentRequestPayload;
import com.yeyito.littlechemistry.network.DeleteContentRequestPayload;
import com.yeyito.littlechemistry.network.DynamicAssetPayload;
import com.yeyito.littlechemistry.network.DynamicAssetRequestPayload;
import com.yeyito.littlechemistry.network.DynamicContentPayload;
import com.yeyito.littlechemistry.network.OpenCreationScreenPayload;
import com.yeyito.littlechemistry.network.OpenDeletionScreenPayload;
import com.yeyito.littlechemistry.particle.DynamicParticleRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LittleChemistry implements ModInitializer {
	public static final String MOD_ID = "little_chemistry";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ResourceKey<Item> WAND_OF_CREATION_KEY = ResourceKey.create(
			Registries.ITEM,
			id("wand_of_creation")
	);

	public static final Item WAND_OF_CREATION = Registry.register(
			BuiltInRegistries.ITEM,
			WAND_OF_CREATION_KEY,
			new WandOfCreationItem(new Item.Properties().setId(WAND_OF_CREATION_KEY).stacksTo(1))
	);

	private static final ResourceKey<Item> WAND_OF_DELETION_KEY = ResourceKey.create(
			Registries.ITEM,
			id("wand_of_deletion")
	);

	public static final Item WAND_OF_DELETION = Registry.register(
			BuiltInRegistries.ITEM,
			WAND_OF_DELETION_KEY,
			new WandOfDeletionItem(new Item.Properties().setId(WAND_OF_DELETION_KEY).stacksTo(1))
	);

	private static final ResourceKey<Item> CRAFTING_TABLE_ON_A_STICK_KEY = ResourceKey.create(
			Registries.ITEM,
			id("crafting_table_on_a_stick")
	);

	public static final Item CRAFTING_TABLE_ON_A_STICK = Registry.register(
			BuiltInRegistries.ITEM,
			CRAFTING_TABLE_ON_A_STICK_KEY,
			new CraftingTableOnAStickItem(
					new Item.Properties().setId(CRAFTING_TABLE_ON_A_STICK_KEY).stacksTo(1))
	);

	private static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			id("main")
	);

	@Override
	public void onInitialize() {
		PortableCraftingComponents.register();
		DynamicContentObjects.register();
		DynamicEntityObjects.register();
		DynamicParticleRegistry.register();
		LittleChemistryCommands.register();
		PayloadTypeRegistry.clientboundPlay().registerLarge(
				DynamicContentPayload.TYPE,
				DynamicContentPayload.CODEC,
				DynamicContentPayload.MAX_DEFINITIONS_BYTES + 1024
		);
		PayloadTypeRegistry.clientboundPlay().registerLarge(
				DynamicAssetPayload.TYPE,
				DynamicAssetPayload.CODEC,
				com.yeyito.littlechemistry.content.DynamicTextureAsset.MAX_ENCODED_BYTES + 128
		);
		PayloadTypeRegistry.clientboundPlay().register(OpenCreationScreenPayload.TYPE, OpenCreationScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDeletionScreenPayload.TYPE, OpenDeletionScreenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DynamicAssetRequestPayload.TYPE, DynamicAssetRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreateContentRequestPayload.TYPE, CreateContentRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().registerLarge(
				DeleteContentRequestPayload.TYPE,
				DeleteContentRequestPayload.CODEC,
				DeleteContentRequestPayload.MAX_ENCODED_BYTES
		);
		ServerPlayNetworking.registerGlobalReceiver(DynamicAssetRequestPayload.TYPE, (payload, context) -> {
			DynamicContentManager manager = DynamicContentManager.active();
			if (manager != null) {
				manager.sendAssets(context.player(), payload.hashes());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(CreateContentRequestPayload.TYPE, (payload, context) -> {
			ItemStack mainHand = context.player().getMainHandItem();
			ItemStack offHand = context.player().getOffhandItem();
			if (mainHand.getItem() != WAND_OF_CREATION && offHand.getItem() != WAND_OF_CREATION) {
				context.player().sendSystemMessage(Component.literal(
						"[Little Chemistry] Hold the Wand of Creation to create content."
				).withStyle(ChatFormatting.RED));
				return;
			}
			LittleChemistryCommands.createFromWand(
					context.player(), payload.contentType(), payload.generationModel(), payload.name());
		});
		ServerPlayNetworking.registerGlobalReceiver(DeleteContentRequestPayload.TYPE, (payload, context) -> {
			ItemStack mainHand = context.player().getMainHandItem();
			ItemStack offHand = context.player().getOffhandItem();
			if (mainHand.getItem() != WAND_OF_DELETION && offHand.getItem() != WAND_OF_DELETION) {
				context.player().sendSystemMessage(Component.literal(
						"[Little Chemistry] Hold the Wand of Deletion to delete content."
				).withStyle(ChatFormatting.RED));
				return;
			}
			LittleChemistryCommands.deleteFromWand(context.player(), payload.names());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(DynamicContentManager::start);
		ServerLifecycleEvents.SERVER_STARTED.register(AiCraftingManager::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(ContentGenerationService::cancelForServer);
		ServerLifecycleEvents.SERVER_STOPPING.register(AiCraftingManager::stop);
		ServerLifecycleEvents.SERVER_STOPPED.register(DynamicContentManager::stop);
		ServerTickEvents.END_SERVER_TICK.register(AiCraftingManager::tick);
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			for (var recipe : player.level().getServer().getRecipeManager().getRecipes()) {
				player.getRecipeBook().add(recipe.id());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DynamicContentManager manager = DynamicContentManager.active();
			if (manager != null) {
				manager.sendSnapshot(handler.getPlayer());
			}
		});

		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				CREATIVE_TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.little_chemistry.main"))
						.icon(() -> new ItemStack(WAND_OF_CREATION))
						.displayItems((context, entries) -> {
							entries.accept(WAND_OF_CREATION);
							entries.accept(WAND_OF_DELETION);
							entries.accept(CRAFTING_TABLE_ON_A_STICK);
							for (var definition : DynamicContentCatalog.definitions()) {
								entries.accept(DynamicContentObjects.createStack(definition));
							}
						})
						.build()
		);

		LOGGER.info("Little Chemistry initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
