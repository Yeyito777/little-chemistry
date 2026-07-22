package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Direct, server-only context for right-clicking a placed generated block. */
public record DynamicPlacedBlockUseContext(
		ServerLevel level,
		ServerPlayer player,
		@Nullable InteractionHand hand,
		ItemStack heldStack,
		BlockPos position,
		BlockState state,
		BlockHitResult hit,
		DynamicContentDefinition definition
) {
	public boolean emptyHand() {
		return hand == null || heldStack.isEmpty();
	}
}
