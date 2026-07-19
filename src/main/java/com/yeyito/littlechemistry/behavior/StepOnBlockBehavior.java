package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked when an entity steps on the generated block. */
public interface StepOnBlockBehavior extends DynamicBehavior {
	void stepOnBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			DynamicContentDefinition definition);
}
