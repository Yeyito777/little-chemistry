package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public final class DynamicContentObjects {
	public static DataComponentType<Identifier> CONTENT_ID;
	public static DynamicCarrierItem ITEM;
	public static DynamicCarrierBlock BLOCK;
	public static DynamicCarrierBlockItem BLOCK_ITEM;
	public static BlockEntityType<DynamicBlockEntity> BLOCK_ENTITY_TYPE;

	private DynamicContentObjects() {
	}

	public static void register() {
		CONTENT_ID = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				LittleChemistry.id("content_id"),
				DataComponentType.<Identifier>builder()
						.persistent(Identifier.CODEC)
						.networkSynchronized(Identifier.STREAM_CODEC)
						.build()
		);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, LittleChemistry.id("dynamic_item"));
		ITEM = Registry.register(
				BuiltInRegistries.ITEM,
				itemKey,
				new DynamicCarrierItem(new Item.Properties().setId(itemKey))
		);

		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, LittleChemistry.id("dynamic_block"));
		BLOCK = Registry.register(
				BuiltInRegistries.BLOCK,
				blockKey,
				new DynamicCarrierBlock(BlockBehaviour.Properties.of().setId(blockKey).strength(1.5F, 6.0F))
		);

		ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, LittleChemistry.id("dynamic_block"));
		BLOCK_ITEM = Registry.register(
				BuiltInRegistries.ITEM,
				blockItemKey,
				new DynamicCarrierBlockItem(BLOCK, new Item.Properties().setId(blockItemKey).useBlockDescriptionPrefix())
		);

		BLOCK_ENTITY_TYPE = Registry.register(
				BuiltInRegistries.BLOCK_ENTITY_TYPE,
				LittleChemistry.id("dynamic_block"),
				new BlockEntityType<>(DynamicBlockEntity::new, Set.of(BLOCK))
		);
	}

	public static ItemStack createStack(DynamicContentDefinition definition) {
		ItemStack stack = new ItemStack(definition.type() == DynamicContentType.BLOCK ? BLOCK_ITEM : ITEM);
		stack.set(CONTENT_ID, LittleChemistry.id(definition.name()));
		return stack;
	}

	public static ItemStack createBlockStack(Identifier contentId) {
		ItemStack stack = new ItemStack(BLOCK_ITEM);
		if (contentId != null) {
			stack.set(CONTENT_ID, contentId);
		}
		return stack;
	}

	public static DynamicContentDefinition definition(ItemStack stack) {
		Identifier contentId = stack.get(CONTENT_ID);
		return contentId == null ? null : DynamicContentCatalog.find(contentId.getPath());
	}
}
