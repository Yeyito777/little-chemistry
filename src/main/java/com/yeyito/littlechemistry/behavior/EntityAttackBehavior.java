package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.entity.Entity;

/** Invoked after a generated entity successfully performs its native melee attack. */
public interface EntityAttackBehavior extends DynamicBehavior {
	void entityAttack(DynamicGeneratedEntityContext context, Entity target);
}
