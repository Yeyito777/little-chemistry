package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Opt-in behavior invoked after the generated item hurts an enemy. */
public interface PostHurtEnemyBehavior extends DynamicBehavior {
	void postHurtEnemy(ServerLevel level, LivingEntity attacker, LivingEntity target,
			ItemStack stack, DynamicContentDefinition definition);
}
