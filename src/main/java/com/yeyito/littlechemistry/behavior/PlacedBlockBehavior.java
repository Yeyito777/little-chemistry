package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Opt-in behavior invoked when the generated content is placed in the world. */
public interface PlacedBlockBehavior extends DynamicBehavior {
	void placedBlock(ServerLevel level, @Nullable LivingEntity placer, BlockPos position,
			BlockState state, ItemStack placedFrom, DynamicContentDefinition definition);
}
