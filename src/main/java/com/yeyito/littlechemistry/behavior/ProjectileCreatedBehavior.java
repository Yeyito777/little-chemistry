package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.entity.projectile.Projectile;

/** Opt-in hook for concept-specific ammunition/projectiles while native bow/crossbow mechanics remain authoritative. */
public interface ProjectileCreatedBehavior extends DynamicBehavior {
	/** Mutate and return the vanilla projectile, or return a compatible replacement in the same level. */
	Projectile projectileCreated(DynamicProjectileContext context);
}
