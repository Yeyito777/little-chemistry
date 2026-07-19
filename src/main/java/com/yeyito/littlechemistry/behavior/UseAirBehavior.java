package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;

/** Opt-in behavior for using the generated item while not targeting a block or entity. */
public interface UseAirBehavior extends DynamicBehavior {
	InteractionResult useAir(DynamicItemUseContext context);
}
