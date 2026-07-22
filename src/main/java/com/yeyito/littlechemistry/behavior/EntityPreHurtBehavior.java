package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.damagesource.DamageSource;

/** Invoked before native damage; return the final nonnegative damage amount, or zero to cancel. */
public interface EntityPreHurtBehavior extends DynamicBehavior {
	float entityPreHurt(DynamicGeneratedEntityContext context, DamageSource source, float amount);
}
