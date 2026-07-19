package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;

/** Opt-in behavior for using the generated block after it has been placed. */
public interface UsePlacedBlockBehavior extends DynamicBehavior {
	InteractionResult usePlacedBlock(DynamicPlacedBlockUseContext context);
}
