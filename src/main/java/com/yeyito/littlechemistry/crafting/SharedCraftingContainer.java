package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Server-owned grid for either a shared physical table or one portable menu. */
public final class SharedCraftingContainer implements CraftingContainer {
	private final AiCraftingManager owner;
	private final AiCraftingManager.@Nullable TableKey key;
	private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
	private final Set<CraftingMenu> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
	private RecipeSignature lockSignature;

	private SharedCraftingContainer(AiCraftingManager owner, AiCraftingManager.@Nullable TableKey key,
			List<ItemStack> initialItems) {
		this.owner = owner;
		this.key = key;
		for (int i = 0; i < Math.min(items.size(), initialItems.size()); i++) {
			items.set(i, initialItems.get(i).copy());
		}
	}

	static SharedCraftingContainer physical(AiCraftingManager owner, AiCraftingManager.TableKey key,
			List<ItemStack> initialItems) {
		return new SharedCraftingContainer(owner, key, initialItems);
	}

	static SharedCraftingContainer portable(AiCraftingManager owner) {
		return new SharedCraftingContainer(owner, null, List.of());
	}

	AiCraftingManager.TableKey key() {
		if (key == null) throw new IllegalStateException("Portable crafting grids do not have a table key");
		return key;
	}

	public boolean isPortable() {
		return key == null;
	}

	public boolean isLocked() {
		return lockSignature != null;
	}

	RecipeSignature lockSignature() {
		return lockSignature;
	}

	void lock(RecipeSignature signature) {
		if (lockSignature != null) throw new IllegalStateException("Crafting table is already locked");
		lockSignature = signature;
		notifyViewers();
	}

	void unlock(RecipeSignature signature) {
		if (lockSignature != null && lockSignature.equals(signature)) {
			lockSignature = null;
			notifyViewers();
		}
	}

	public void addViewer(CraftingMenu menu) {
		viewers.add(menu);
	}

	public void removeViewer(CraftingMenu menu) {
		viewers.remove(menu);
		owner.tableViewerClosed(this);
	}

	List<CraftingMenu> viewers() {
		return List.copyOf(viewers);
	}

	boolean hasViewers() {
		return !viewers.isEmpty();
	}

	NonNullList<ItemStack> copyItems() {
		NonNullList<ItemStack> copy = NonNullList.withSize(items.size(), ItemStack.EMPTY);
		for (int i = 0; i < items.size(); i++) copy.set(i, items.get(i).copy());
		return copy;
	}

	NonNullList<ItemStack> drain() {
		NonNullList<ItemStack> removed = copyItems();
		items.clear();
		lockSignature = null;
		notifyViewers();
		return removed;
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack item : items) if (!item.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (isLocked()) return ItemStack.EMPTY;
		ItemStack removed = ContainerHelper.takeItem(items, slot);
		if (!removed.isEmpty()) changed();
		return removed;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		if (isLocked()) return ItemStack.EMPTY;
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		if (!removed.isEmpty()) changed();
		return removed;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (isLocked()) return;
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		changed();
	}

	@Override
	public void setChanged() {
		if (!isLocked()) changed();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		if (isLocked()) return;
		items.clear();
		changed();
	}

	@Override
	public int getWidth() {
		return 3;
	}

	@Override
	public int getHeight() {
		return 3;
	}

	@Override
	public List<ItemStack> getItems() {
		return List.copyOf(items);
	}

	@Override
	public void fillStackedContents(StackedItemContents contents) {
		for (ItemStack stack : items) contents.accountSimpleStack(stack);
	}

	private void changed() {
		owner.tableContentsChanged(this);
	}

	private void notifyViewers() {
		owner.tableViewStateChanged(this);
	}
}
