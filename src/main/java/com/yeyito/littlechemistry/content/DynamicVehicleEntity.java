package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Goal-free rideable carrier controlled explicitly through generated behavior and DynamicRiderInput. */
public class DynamicVehicleEntity extends DynamicCarrierEntity {
	public DynamicVehicleEntity(EntityType<? extends DynamicVehicleEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerMovementGoal() {
		// Vehicles must not wander independently. Generated EntityTickBehavior owns propulsion and steering.
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity passenger ? passenger : null;
	}
}
