package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Optional, read-only override for player insertion and extraction rules on a generated workstation slot. */
public interface WorkstationSlotBehavior extends DynamicBehavior {
	boolean canUseWorkstationSlot(DynamicWorkstationContext context, ServerPlayer player, String slotId,
			ItemStack stack, WorkstationSlotAction action);
}
