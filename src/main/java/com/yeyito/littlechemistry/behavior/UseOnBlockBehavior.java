package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;

/** Opt-in behavior for using the generated item on a block. */
public interface UseOnBlockBehavior extends DynamicBehavior {
	InteractionResult useOnBlock(DynamicBlockUseContext context);
}
