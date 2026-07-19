package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior for left-clicking the generated block after it has been placed. */
public interface AttackPlacedBlockBehavior extends DynamicBehavior {
	void attackPlacedBlock(ServerLevel level, ServerPlayer player, BlockPos position,
			BlockState state, DynamicContentDefinition definition);
}
