package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** Hostile rideable carrier with vanilla Enemy interoperability. */
public final class DynamicVehicleMonsterEntity extends DynamicVehicleEntity implements Enemy {
	public DynamicVehicleMonsterEntity(EntityType<? extends DynamicVehicleMonsterEntity> type, Level level) {
		super(type, level);
	}
}
