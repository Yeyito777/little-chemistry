package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.damagesource.DamageSource;

/** Invoked when a generated entity dies, before its carrier is removed. */
public interface EntityDeathBehavior extends DynamicBehavior {
	void entityDeath(DynamicGeneratedEntityContext context, DamageSource source);
}
