package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.damagesource.DamageSource;

/** Invoked after a generated entity accepts damage. */
public interface EntityHurtBehavior extends DynamicBehavior {
	void entityHurt(DynamicGeneratedEntityContext context, DamageSource source, float amount);
}
