package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Direct, server-only context for right-clicking a generated stack in air. */
public record DynamicItemUseContext(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack stack,
		DynamicContentDefinition definition
) {
}
