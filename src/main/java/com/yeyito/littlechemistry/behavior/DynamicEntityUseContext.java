package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Direct, server-only context for using a generated stack on an entity. */
public record DynamicEntityUseContext(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack stack,
		LivingEntity target,
		DynamicContentDefinition definition
) {
}
