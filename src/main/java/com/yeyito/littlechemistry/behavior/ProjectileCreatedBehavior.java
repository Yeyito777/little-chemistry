package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.entity.projectile.Projectile;

/** Opt-in transformation hook inside the native projectile route; custom mechanics may instead own the full lifecycle. */
public interface ProjectileCreatedBehavior extends DynamicBehavior {
	/** Mutate and return the vanilla projectile, or use context.replacement/context.firework for a different outgoing shot. */
	Projectile projectileCreated(DynamicProjectileContext context);
}
