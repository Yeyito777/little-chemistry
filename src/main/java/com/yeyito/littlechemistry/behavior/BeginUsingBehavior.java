package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;

/** Invoked before a generated item enters Minecraft's held-use lifecycle. */
public interface BeginUsingBehavior extends DynamicBehavior {
	InteractionResult beginUsing(DynamicItemUseContext context);
}
