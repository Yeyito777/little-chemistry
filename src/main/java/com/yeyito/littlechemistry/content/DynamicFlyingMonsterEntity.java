package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** Hostile flying carrier with vanilla Enemy interoperability and monster-category semantics. */
public final class DynamicFlyingMonsterEntity extends DynamicFlyingEntity implements Enemy {
	public DynamicFlyingMonsterEntity(EntityType<? extends DynamicFlyingMonsterEntity> type, Level level) {
		super(type, level);
	}
}
