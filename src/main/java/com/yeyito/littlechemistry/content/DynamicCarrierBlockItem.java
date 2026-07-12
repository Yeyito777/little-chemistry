package com.yeyito.littlechemistry.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class DynamicCarrierBlockItem extends BlockItem {
	public DynamicCarrierBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Block")
				: Component.literal(definition.displayName());
	}
}
