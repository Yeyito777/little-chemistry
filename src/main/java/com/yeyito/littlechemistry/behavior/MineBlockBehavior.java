package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked after mining a block with the generated item. */
public interface MineBlockBehavior extends DynamicBehavior {
	void mineBlock(ServerLevel level, LivingEntity miner, BlockPos position, BlockState state,
			ItemStack stack, DynamicContentDefinition definition);
}
