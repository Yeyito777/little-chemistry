package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/** Opt-in behavior invoked when a neighboring block changes. */
public interface NeighborChangedBlockBehavior extends DynamicBehavior {
	void neighborChangedBlock(ServerLevel level, BlockPos position, BlockState state, Block neighbor,
			@Nullable Orientation orientation, boolean movedByPiston, DynamicContentDefinition definition);
}
