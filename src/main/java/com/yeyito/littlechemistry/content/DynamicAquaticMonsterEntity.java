package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** Hostile water-native carrier with vanilla Enemy interoperability. */
public final class DynamicAquaticMonsterEntity extends DynamicAquaticEntity implements Enemy {
	public DynamicAquaticMonsterEntity(EntityType<? extends DynamicAquaticMonsterEntity> type, Level level) {
		super(type, level);
	}
}
