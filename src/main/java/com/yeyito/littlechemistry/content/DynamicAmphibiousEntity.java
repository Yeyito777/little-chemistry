package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/** Land-and-water carrier with amphibious pathfinding. */
public class DynamicAmphibiousEntity extends DynamicCarrierEntity {
	public DynamicAmphibiousEntity(EntityType<? extends DynamicAmphibiousEntity> type, Level level) {
		super(type, level);
		moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.04F, 0.15F, true);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new AmphibiousPathNavigation(this, level);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected void registerMovementGoal() {
		goalSelector.addGoal(6, new RandomSwimmingGoal(this, 1.0, 40));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
	}
}
