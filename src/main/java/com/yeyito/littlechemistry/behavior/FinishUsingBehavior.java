package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Opt-in behavior invoked after the generated item finishes being consumed or used. */
public interface FinishUsingBehavior extends DynamicBehavior {
	ItemStack finishUsing(ServerLevel level, LivingEntity consumer, ItemStack originalStack,
			ItemStack resultStack, DynamicContentDefinition definition);
}
