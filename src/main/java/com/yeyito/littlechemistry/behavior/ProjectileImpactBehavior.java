package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

/** Opt-in impact callback for a projectile launched by a generated bow or crossbow. */
public interface ProjectileImpactBehavior extends DynamicBehavior {
	void projectileImpact(ServerLevel level, Projectile projectile, HitResult hit, DynamicContentDefinition definition);
}
