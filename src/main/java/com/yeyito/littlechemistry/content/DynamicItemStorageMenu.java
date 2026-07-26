package com.yeyito.littlechemistry.content;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-side generic-chest menu that pins the portable-storage carrier while its own inventory is open. */
final class DynamicItemStorageMenu extends ChestMenu {
	private final Container storage;
	private final int storageSlots;
	private final int carrierInventorySlot;
	private final int carrierMenuSlot;

	DynamicItemStorageMenu(MenuType<?> type, int containerId, Inventory inventory, Container storage,
			int rows, int carrierInventorySlot) {
		super(type, containerId, inventory, storage, rows);
		this.storage = storage;
		this.storageSlots = rows * 9;
		this.carrierInventorySlot = carrierInventorySlot;
		for (int index = 0; index < storageSlots; index++) {
			Slot original = slots.get(index);
			GuardedStorageSlot guarded = new GuardedStorageSlot(storage, index, original.x, original.y);
			guarded.index = original.index;
			slots.set(index, guarded);
		}
		this.carrierMenuSlot = carrierInventorySlot < 0
				? -1 : findSlot(inventory, carrierInventorySlot).orElse(-1);
		if (carrierMenuSlot >= 0) {
			Slot original = slots.get(carrierMenuSlot);
			PinnedCarrierSlot pinned = new PinnedCarrierSlot(
					inventory, carrierInventorySlot, original.x, original.y);
			pinned.index = original.index;
			slots.set(carrierMenuSlot, pinned);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		if (slot == carrierMenuSlot || !stillValid(player)) return ItemStack.EMPTY;
		if (slot >= storageSlots && slot < slots.size() && !storage.canPlaceItem(0, slots.get(slot).getItem())) {
			return ItemStack.EMPTY;
		}
		return super.quickMoveStack(player, slot);
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player player) {
		if (!stillValid(player)) {
			if (player instanceof ServerPlayer serverPlayer) serverPlayer.closeContainer();
			return;
		}
		if (slot == carrierMenuSlot) return;
		// Number-key and offhand swaps name the target inventory index in button. Block any swap that could move the
		// carrier even when the offhand slot is not represented by the generic chest screen.
		if (input == ContainerInput.SWAP && button == carrierInventorySlot) return;
		super.clicked(slot, button, input, player);
	}

	private static final class PinnedCarrierSlot extends Slot {
		private PinnedCarrierSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override public boolean mayPickup(Player player) { return false; }
		@Override public boolean mayPlace(ItemStack stack) { return false; }
		@Override public boolean allowModification(Player player) { return false; }
	}

	/** ChestMenu otherwise uses plain slots whose mayPlace does not consult Container.canPlaceItem. */
	static final class GuardedStorageSlot extends Slot {
		GuardedStorageSlot(Container storage, int index, int x, int y) {
			super(storage, index, x, y);
		}

		@Override public boolean mayPlace(ItemStack stack) { return container.canPlaceItem(getContainerSlot(), stack); }
	}
}
