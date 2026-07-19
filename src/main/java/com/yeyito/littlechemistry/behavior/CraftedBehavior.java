package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Opt-in behavior invoked when the generated item is crafted. */
public interface CraftedBehavior extends DynamicBehavior {
	void crafted(ServerLevel level, ServerPlayer player, ItemStack stack,
			DynamicContentDefinition definition);
}
