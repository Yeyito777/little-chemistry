package com.yeyito.littlechemistry.behavior;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Optional, read-only override for hopper, {@code WorldlyContainer}, and Fabric item-transfer access. */
public interface WorkstationAutomationBehavior extends DynamicBehavior {
	boolean canAutomateWorkstationSlot(DynamicWorkstationContext context, String slotId, ItemStack stack,
			WorkstationSlotAction action, @Nullable Direction side);
}
