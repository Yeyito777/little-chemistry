package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked when an entity falls on the generated block. */
public interface FallOnBlockBehavior extends DynamicBehavior {
	void fallOnBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			double fallDistance, DynamicContentDefinition definition);
}
