package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Opt-in behavior invoked when a projectile hits the generated block. */
public interface ProjectileHitBlockBehavior extends DynamicBehavior {
	void projectileHitBlock(ServerLevel level, BlockPos position, BlockState state,
			BlockHitResult hit, Projectile projectile, DynamicContentDefinition definition);
}
