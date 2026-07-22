package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** Hostile amphibious carrier with vanilla Enemy interoperability. */
public final class DynamicAmphibiousMonsterEntity extends DynamicAmphibiousEntity implements Enemy {
	public DynamicAmphibiousMonsterEntity(EntityType<? extends DynamicAmphibiousMonsterEntity> type, Level level) {
		super(type, level);
	}
}
