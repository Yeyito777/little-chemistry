package com.yeyito.littlechemistry.behavior;

/** Invoked once after a fresh generated entity has been added to the world. */
public interface EntitySpawnedBehavior extends DynamicBehavior {
	void entitySpawned(DynamicGeneratedEntityContext context);
}
