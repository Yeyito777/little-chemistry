package com.yeyito.littlechemistry.behavior;

/** Optional server tick while a generated custom projectile weapon is being used/charged. */
public interface ProjectileWeaponUseTickBehavior extends DynamicBehavior {
	void projectileWeaponUseTick(DynamicProjectileWeaponContext context);
}
