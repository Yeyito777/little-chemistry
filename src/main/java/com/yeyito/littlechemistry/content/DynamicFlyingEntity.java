package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/** Flying carrier family with native aerial navigation. */
public class DynamicFlyingEntity extends DynamicCarrierEntity {
	public DynamicFlyingEntity(EntityType<? extends DynamicFlyingEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		setNoGravity(true);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new FlyingPathNavigation(this, level);
	}

	@Override
	protected void registerMovementGoal() {
		goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
	}
}
