package com.yeyito.littlechemistry.behavior;

/** Invoked every authoritative server tick for a generated entity. */
public interface EntityTickBehavior extends DynamicBehavior {
	void entityTick(DynamicGeneratedEntityContext context);
}
