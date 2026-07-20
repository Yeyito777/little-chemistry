package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;

/** Optional handler for custom, data-defined workstation buttons. The engine owns its mandatory recipe button. */
public interface WorkstationButtonBehavior extends DynamicBehavior {
	boolean workstationButtonPressed(DynamicWorkstationContext context, ServerPlayer player, String buttonId);
}
