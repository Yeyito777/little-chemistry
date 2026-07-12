package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DynamicCarrierBlock extends Block implements EntityBlock {
	public DynamicCarrierBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
		return new DynamicBlockEntity(position, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos position, BlockState state, boolean includeData) {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity blockEntity) {
			return DynamicContentObjects.createBlockStack(blockEntity.contentId());
		}
		return new ItemStack(DynamicContentObjects.BLOCK_ITEM);
	}

}
