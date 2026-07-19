package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;

/** Opt-in behavior for using the generated item on a living entity. */
public interface InteractLivingEntityBehavior extends DynamicBehavior {
	InteractionResult interactLivingEntity(DynamicEntityUseContext context);
}
