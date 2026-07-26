package com.yeyito.littlechemistry.content;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.UUID;

/** Mutable menu view whose contents are persisted directly on one max-stack-one generated storage item. */
final class DynamicItemStorageContainer implements Container {
	private final ServerPlayer player;
	private final UUID storageId;
	private final int carrierInventorySlot;
	private final boolean acceptsContainerItems;
	private final NonNullList<ItemStack> items;

	DynamicItemStorageContainer(ServerPlayer player, UUID storageId, int carrierInventorySlot, DynamicStorageSpec storage) {
		this.player = player;
		this.storageId = storageId;
		this.carrierInventorySlot = carrierInventorySlot;
		this.acceptsContainerItems = storage.acceptsContainerItems();
		this.items = NonNullList.withSize(storage.slots(), ItemStack.EMPTY);
		liveCarrier().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
	}

	@Override public int getContainerSize() { return items.size(); }
	@Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
	@Override public ItemStack getItem(int slot) {
		return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
	}
	@Override public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}
	@Override public ItemStack removeItemNoUpdate(int slot) {
		ItemStack removed = ContainerHelper.takeItem(items, slot);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}
	@Override public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= items.size() || !mayStore(stack)) return;
		items.set(slot, stack.copy());
		items.get(slot).limitSize(getMaxStackSize(items.get(slot)));
		setChanged();
	}
	@Override public boolean canPlaceItem(int slot, ItemStack stack) { return mayStore(stack); }
	@Override public void setChanged() {
		ItemStack carrier = liveCarrier();
		if (!carrier.isEmpty()) {
			carrier.set(DataComponents.CONTAINER,
					isEmpty() ? ItemContainerContents.EMPTY : ItemContainerContents.fromItems(items));
			player.getInventory().setChanged();
		}
	}
	@Override public boolean stillValid(Player player) { return player == this.player && !liveCarrier().isEmpty(); }
	@Override public void clearContent() { items.clear(); setChanged(); }

	private boolean mayStore(ItemStack stack) {
		// Container-bearing items are rejected generally, not only by ID, so generated storage cannot nest itself or
		// another portable inventory and create recursive/oversized component trees.
		return stack.isEmpty() || acceptsContainerItems || !stack.has(DataComponents.CONTAINER);
	}

	private ItemStack liveCarrier() {
		ItemStack stack = player.getInventory().getItem(carrierInventorySlot);
		return storageId.equals(stack.get(DynamicContentObjects.STORAGE_ID)) ? stack : ItemStack.EMPTY;
	}
}
