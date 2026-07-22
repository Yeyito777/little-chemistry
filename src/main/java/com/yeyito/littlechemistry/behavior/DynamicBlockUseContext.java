package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Direct, server-only context for using a generated stack on a block. */
public record DynamicBlockUseContext(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack stack,
		DynamicContentDefinition definition,
		BlockPos clickedPos,
		Direction clickedFace,
		Vec3 clickLocation
) {
	public DynamicItemState state() {
		return DynamicItemState.of(stack);
	}

	public DynamicActionInput actionInput() {
		return DynamicActionInput.capture(player);
	}

	public BlockPos adjacentPos() {
		return clickedPos.relative(clickedFace);
	}
}
