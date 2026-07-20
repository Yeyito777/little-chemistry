package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/** Invoked when a player interacts with a generated entity. */
public interface EntityInteractBehavior extends DynamicBehavior {
	InteractionResult entityInteract(DynamicGeneratedEntityContext context, ServerPlayer player,
			InteractionHand hand, ItemStack heldStack);
}
