package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.entity.Entity;

/** Invoked before a generated mob's native attack; false cancels the native attack. */
public interface EntityPreAttackBehavior extends DynamicBehavior {
	boolean entityPreAttack(DynamicGeneratedEntityContext context, Entity target);
}
