package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-owned grid for either a shared physical table or one persistent portable item. */
public final class SharedCraftingContainer implements CraftingContainer {
	private final AiCraftingManager owner;
	private final AiCraftingManager.@Nullable TableKey key;
	private final @Nullable UUID portableId;
	private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
	private final Set<CraftingMenu> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<ItemStack> portableCarriers = Collections.newSetFromMap(new IdentityHashMap<>());
	private RecipeSignature lockSignature;
	private boolean recipeReady;

	private SharedCraftingContainer(AiCraftingManager owner, AiCraftingManager.@Nullable TableKey key,
			@Nullable UUID portableId, List<ItemStack> initialItems, boolean recipeReady) {
		this.owner = owner;
		this.key = key;
		this.portableId = portableId;
		for (int i = 0; i < Math.min(items.size(), initialItems.size()); i++) {
			items.set(i, initialItems.get(i).copy());
		}
		this.recipeReady = recipeReady && !isEmpty();
	}

	static SharedCraftingContainer physical(AiCraftingManager owner, AiCraftingManager.TableKey key,
			List<ItemStack> initialItems) {
		return physical(owner, key, initialItems, false);
	}

	static SharedCraftingContainer physical(AiCraftingManager owner, AiCraftingManager.TableKey key,
			List<ItemStack> initialItems, boolean recipeReady) {
		return new SharedCraftingContainer(owner, key, null, initialItems, recipeReady);
	}

	static SharedCraftingContainer portable(AiCraftingManager owner, UUID portableId,
			List<ItemStack> initialItems, boolean recipeReady) {
		return new SharedCraftingContainer(owner, null, portableId, initialItems, recipeReady);
	}

	AiCraftingManager.TableKey key() {
		if (key == null) throw new IllegalStateException("Portable crafting grids do not have a table key");
		return key;
	}

	public boolean isPortable() {
		return key == null;
	}

	UUID portableId() {
		if (portableId == null) throw new IllegalStateException("Physical crafting tables do not have a portable ID");
		return portableId;
	}

	public boolean isLocked() {
		return lockSignature != null;
	}

	boolean isRecipeReady() {
		return recipeReady;
	}

	boolean hasGenerationParticles() {
		return !isPortable() && (isLocked() || recipeReady);
	}

	void lock(RecipeSignature signature) {
		if (lockSignature != null) throw new IllegalStateException("Crafting table is already locked");
		recipeReady = false;
		lockSignature = signature;
		syncPortableCarriers();
		owner.tableGenerationStateChanged(this);
		notifyViewers();
	}

	void unlock(RecipeSignature signature) {
		finishGeneration(signature, false);
	}

	void finishGeneration(RecipeSignature signature, boolean succeeded) {
		if (lockSignature != null && lockSignature.equals(signature)) {
			lockSignature = null;
			recipeReady = succeeded;
			syncPortableCarriers();
			owner.tableGenerationStateChanged(this);
			notifyViewers();
		}
	}

	void clearRecipeReady() {
		if (!recipeReady) return;
		recipeReady = false;
		syncPortableCarriers();
		owner.tableGenerationStateChanged(this);
		notifyViewers();
	}

	public void attachPortableCarrier(ItemStack carrier) {
		if (!isPortable()) throw new IllegalStateException("Cannot attach an item to a physical crafting table");
		portableCarriers.add(carrier);
		syncPortableCarrier(carrier);
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
		recipeReady = false;
		syncPortableCarriers();
		owner.tableGenerationStateChanged(this);
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
		if (isLocked() || !mayPlace(stack)) return;
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
		clearRecipeReady();
		syncPortableCarriers();
		owner.tableContentsChanged(this);
	}

	boolean mayPlace(ItemStack stack) {
		if (isLocked()) return false;
		return !isPortable() || stack.isEmpty()
				|| !portableId().equals(stack.get(PortableCraftingComponents.TABLE_ID));
	}

	private void syncPortableCarriers() {
		if (!isPortable()) return;
		portableCarriers.removeIf(carrier -> !syncPortableCarrier(carrier));
	}

	private boolean syncPortableCarrier(ItemStack carrier) {
		if (carrier.isEmpty() || !portableId().equals(carrier.get(PortableCraftingComponents.TABLE_ID))) return false;

		if (isEmpty()) carrier.remove(DataComponents.CONTAINER);
		else carrier.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

		if (isLocked()) carrier.set(PortableCraftingComponents.STATE, PortableCraftingState.GENERATING);
		else if (recipeReady) carrier.set(PortableCraftingComponents.STATE, PortableCraftingState.READY);
		else carrier.remove(PortableCraftingComponents.STATE);
		return true;
	}

	private void notifyViewers() {
		owner.tableViewStateChanged(this);
	}
}
