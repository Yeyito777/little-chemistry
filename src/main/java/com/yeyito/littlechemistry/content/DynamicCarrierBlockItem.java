package com.yeyito.littlechemistry.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

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

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
		if (state == null || definition == null || definition.block() == null) {
			return state;
		}
		return state
				.setValue(DynamicCarrierBlock.LIGHT_LEVEL, definition.block().lightLevel())
				.setValue(DynamicCarrierBlock.MATERIAL, definition.block().material());
	}
}
