package com.yeyito.littlechemistry.behavior;

/** Invoked every authoritative server tick while a generated item remains in use. */
public interface UsingTickBehavior extends DynamicBehavior {
	void usingTick(DynamicItemUsingContext context);
}
