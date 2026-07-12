package com.yeyito.littlechemistry;

import com.yeyito.littlechemistry.item.WandOfCreationItem;
import com.yeyito.littlechemistry.command.LittleChemistryCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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

	private static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			id("main")
	);

	@Override
	public void onInitialize() {
		LittleChemistryCommands.register();

		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				CREATIVE_TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.little_chemistry.main"))
						.icon(() -> new ItemStack(WAND_OF_CREATION))
						.displayItems((context, entries) -> entries.accept(WAND_OF_CREATION))
						.build()
		);

		LOGGER.info("Little Chemistry initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
