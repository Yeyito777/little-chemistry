package com.yeyito.littlechemistry.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class LockedCraftingSlot extends Slot {
	private final SharedCraftingContainer table;

	public LockedCraftingSlot(SharedCraftingContainer table, int slot, int x, int y) {
		super(table, slot, x, y);
		this.table = table;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return table.mayPlace(stack);
	}

	@Override
	public boolean mayPickup(Player player) {
		return !table.isLocked();
	}

	@Override
	public boolean allowModification(Player player) {
		return !table.isLocked() && super.allowModification(player);
	}
}
