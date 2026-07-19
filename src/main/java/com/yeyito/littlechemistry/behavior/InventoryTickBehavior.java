package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Opt-in behavior for server inventory ticks. */
public interface InventoryTickBehavior extends DynamicBehavior {
	void inventoryTick(ServerLevel level, Entity owner, @Nullable EquipmentSlot slot,
			ItemStack stack, DynamicContentDefinition definition);
}
