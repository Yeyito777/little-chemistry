package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicCarrierEntity;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative server context shared by generated-entity callbacks. */
public record DynamicGeneratedEntityContext(
		ServerLevel level,
		DynamicCarrierEntity entity,
		DynamicContentDefinition definition,
		DynamicEntityState state
) {
	/**
	 * Returns the first server-player passenger's latest synchronized controls, or {@link DynamicRiderInput#NONE} when
	 * this entity currently has no player rider. This is the authoritative input API for generated rideable entities;
	 * server-side behavior must not read the inherited {@code xxa}/{@code zza} living-entity fields.
	 */
	public DynamicRiderInput riderInput() {
		return entity.getFirstPassenger() instanceof ServerPlayer rider
				? DynamicRiderInput.capture(rider) : DynamicRiderInput.NONE;
	}

	/** Generic remappable ability keys from the controlling player, independent of vanilla movement input. */
	public DynamicActionInput actionInput() {
		return entity.getFirstPassenger() instanceof ServerPlayer rider
				? DynamicActionInput.capture(rider) : DynamicActionInput.NONE;
	}

	/** State intended to drive client-visible model/animation selectors as well as server behavior. */
	public DynamicSynchronizedEntityState synchronizedState() {
		return new DynamicSynchronizedEntityState(entity);
	}

	public net.minecraft.world.entity.ai.goal.GoalSelector goals() {
		return entity.getGoalSelector();
	}

	public net.minecraft.world.entity.ai.goal.GoalSelector targetGoals() {
		return entity.targetGoalSelector();
	}
}
