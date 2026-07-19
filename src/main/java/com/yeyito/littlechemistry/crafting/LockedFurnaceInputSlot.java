package com.yeyito.littlechemistry.crafting;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** Keeps the furnace ingredient stable while its recipe is being generated. */
public final class LockedFurnaceInputSlot extends Slot {
	private final BooleanSupplier locked;

	public LockedFurnaceInputSlot(Container container, int slot, int x, int y, BooleanSupplier locked) {
		super(container, slot, x, y);
		this.locked = locked;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !locked.getAsBoolean();
	}

	@Override
	public boolean mayPickup(Player player) {
		return !locked.getAsBoolean();
	}

	@Override
	public boolean allowModification(Player player) {
		return !locked.getAsBoolean() && super.allowModification(player);
	}
}
