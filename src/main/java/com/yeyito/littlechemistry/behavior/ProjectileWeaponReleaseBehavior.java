package com.yeyito.littlechemistry.behavior;

/** Full custom release action for a generated bow/crossbow that selected custom mechanics. */
public interface ProjectileWeaponReleaseBehavior extends DynamicBehavior {
	boolean projectileWeaponRelease(DynamicProjectileWeaponContext context);
}
