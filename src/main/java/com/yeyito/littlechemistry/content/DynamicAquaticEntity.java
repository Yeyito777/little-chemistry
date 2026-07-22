package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Water-native carrier with swimming controls and navigation. */
public class DynamicAquaticEntity extends DynamicCarrierEntity {
	public DynamicAquaticEntity(EntityType<? extends DynamicAquaticEntity> type, Level level) {
		super(type, level);
		moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.02F, 0.1F, true);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected void registerMovementGoal() {
		goalSelector.addGoal(6, new RandomSwimmingGoal(this, 1.0, 40));
	}

	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		moveRelative(0.01F, input);
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(getDeltaMovement().scale(0.9));
		if (getTarget() == null) setDeltaMovement(getDeltaMovement().add(0.0, -0.005, 0.0));
	}
}
