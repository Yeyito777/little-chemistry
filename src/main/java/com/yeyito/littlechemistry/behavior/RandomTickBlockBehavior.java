package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked on random ticks for the generated block. */
public interface RandomTickBlockBehavior extends DynamicBehavior {
	void randomTickBlock(ServerLevel level, BlockPos position, BlockState state, RandomSource random,
			DynamicContentDefinition definition);
}
