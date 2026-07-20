package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** Hostile ground carrier with vanilla Enemy interoperability and monster-category semantics. */
public final class DynamicGroundMonsterEntity extends DynamicCarrierEntity implements Enemy {
	public DynamicGroundMonsterEntity(EntityType<? extends DynamicGroundMonsterEntity> type, Level level) {
		super(type, level);
	}
}
