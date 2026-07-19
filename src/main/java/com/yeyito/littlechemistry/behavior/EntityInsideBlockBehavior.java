package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in behavior invoked while an entity intersects the generated block. */
public interface EntityInsideBlockBehavior extends DynamicBehavior {
	void entityInsideBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			InsideBlockEffectApplier effects, boolean isEntry, DynamicContentDefinition definition);
}
