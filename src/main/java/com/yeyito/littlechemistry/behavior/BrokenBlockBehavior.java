package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked when the generated block is broken. */
public interface BrokenBlockBehavior extends DynamicBehavior {
	void brokenBlock(ServerLevel level, ServerPlayer player, BlockPos position, BlockState state,
			ItemStack tool, DynamicContentDefinition definition);
}
