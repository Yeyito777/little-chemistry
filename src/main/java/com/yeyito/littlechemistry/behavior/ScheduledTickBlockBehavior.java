package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked on scheduled ticks for the generated block. */
public interface ScheduledTickBlockBehavior extends DynamicBehavior {
	void scheduledTickBlock(ServerLevel level, BlockPos position, BlockState state, RandomSource random,
			DynamicContentDefinition definition);
}
