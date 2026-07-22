package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicWorkstationContext;
import com.yeyito.littlechemistry.behavior.DynamicWorkstationRuntimeAccess;
import com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest;
import com.yeyito.littlechemistry.behavior.WorkstationRecipeStatus;
import com.yeyito.littlechemistry.behavior.WorkstationSlotAction;
import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiWorkstationRecipe;
import com.yeyito.littlechemistry.crafting.DynamicWorkstationMenu;
import com.yeyito.littlechemistry.crafting.WorkstationOpenData;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeSignature;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Carrier block entity plus optional persistent runtime for an AI-defined workstation. */
public final class DynamicBlockEntity extends BlockEntity implements WorldlyContainer,
		ExtendedMenuProvider<WorkstationOpenData>, DynamicWorkstationRuntimeAccess {
	private static final int MAX_SLOTS = DynamicWorkstationSpec.MAX_SLOTS;
	private static final int MAX_PERSISTENT_KEYS = 64;
	private static final Codec<Map<String, Long>> LONG_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.LONG);
	private static final Codec<Map<String, Integer>> UI_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.INT);
	private static final Codec<Map<String, String>> GENERATED_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.STRING);
	private static final Set<DynamicBlockEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private Identifier contentId;
	private NonNullList<ItemStack> workstationItems = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
	private final Map<String, Long> persistentState = new LinkedHashMap<>();
	private final Map<String, Integer> uiState = new LinkedHashMap<>();
	private final Map<String, String> generatedState = new LinkedHashMap<>();
	private @Nullable WorkstationRecipeRequest currentRequest;
	private @Nullable WorkstationRecipeSignature currentSignature;
	private @Nullable AiWorkstationRecipe currentRecipe;
	private WorkstationRecipeStatus recipeStatus = WorkstationRecipeStatus.NONE;
	private boolean recipeDirty = true;
	private boolean capturingRecipe;
	private boolean evaluatingSlotRule;

	public DynamicBlockEntity(BlockPos position, BlockState state) {
		super(DynamicContentObjects.BLOCK_ENTITY_TYPE, position, state);
		synchronized (LIVE) {
			LIVE.add(this);
		}
	}

	public Identifier contentId() {
		return contentId;
	}

	public DynamicContentDefinition workstationDefinition() {
		DynamicContentDefinition definition = contentId == null ? null : DynamicContentCatalog.find(contentId);
		return definition != null && definition.workstation() != null ? definition : null;
	}

	public boolean isWorkstation() {
		return workstationDefinition() != null;
	}

	public DynamicWorkstationContext workstationContext() {
		if (!(level instanceof ServerLevel serverLevel)) {
			throw new IllegalStateException("Workstation context exists only on the server");
		}
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) throw new IllegalStateException("Block is not a valid workstation");
		return new DynamicWorkstationContext(serverLevel, worldPosition, getBlockState(), definition, this);
	}

	public boolean isValidWorkstation(Player player) {
		return workstationDefinition() != null && level instanceof ServerLevel
				&& level.getBlockEntity(worldPosition) == this && Container.stillValidBlockEntity(this, player);
	}

	public boolean isWorkstationLocked() {
		if (level instanceof ServerLevel serverLevel) {
			AiCraftingManager manager = AiCraftingManager.active();
			return manager != null && manager.isWorkstationLocked(serverLevel, worldPosition);
		}
		return false;
	}

	public void lockWorkstation(WorkstationRecipeSignature signature) {
		if (signature == null) throw new IllegalArgumentException("Workstation lock signature is required");
		recipeStatus = WorkstationRecipeStatus.GENERATING;
		recipeDirty = false;
		super.setChanged();
	}

	public void finishWorkstationGeneration(WorkstationRecipeSignature signature, boolean succeeded) {
		if (signature == null) return;
		recipeDirty = succeeded;
		if (succeeded) {
			refreshRecipe();
		} else {
			currentRecipe = null;
			recipeStatus = WorkstationRecipeStatus.FAILED;
		}
		super.setChanged();
	}

	public static void serverTick(ServerLevel level, BlockPos position, BlockState state, DynamicBlockEntity entity) {
		if (entity.contentId != null && DynamicContentCatalog.find(entity.contentId) == null) {
			entity.removeUnavailableDynamicStacks();
			if (!entity.isEmpty()) {
				net.minecraft.world.Containers.dropContents(level, position, entity.drainWorkstationItems());
			}
			level.removeBlock(position, false);
			return;
		}
		DynamicContentDefinition definition = entity.workstationDefinition();
		if (definition == null) return;
		entity.ensureWorkstationState(definition.workstation());
		entity.refreshRecipe();
		if (!entity.isWorkstationLocked()) {
			DynamicBehaviorRegistry.workstationTick(definition, entity.workstationContext());
		}
	}

	private void refreshRecipe() {
		if (!recipeDirty) return;
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null || !(level instanceof ServerLevel)) {
			currentRequest = null;
			currentSignature = null;
			currentRecipe = null;
			if (!isWorkstationLocked()) recipeStatus = WorkstationRecipeStatus.NONE;
			recipeDirty = false;
			return;
		}
		if (isWorkstationLocked()) {
			recipeStatus = WorkstationRecipeStatus.GENERATING;
			recipeDirty = false;
			return;
		}
		recipeDirty = false;
		try {
			currentRequest = captureWorkstationRecipe(null);
			currentSignature = WorkstationRecipeSignature.capture(definition, currentRequest, this);
			AiCraftingManager manager = AiCraftingManager.active();
			currentRecipe = manager == null ? null : manager.findWorkstationRecipe(currentSignature);
			recipeStatus = currentRecipe == null ? WorkstationRecipeStatus.NONE : WorkstationRecipeStatus.READY;
		} catch (IllegalArgumentException invalid) {
			currentRequest = null;
			currentSignature = null;
			currentRecipe = null;
			recipeStatus = WorkstationRecipeStatus.FAILED;
		}
	}

	/** Runs the recipe-capture callback under a read-only guard. */
	public @Nullable WorkstationRecipeRequest captureWorkstationRecipe(@Nullable ServerPlayer player) {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) return null;
		if (capturingRecipe) throw new IllegalStateException("Recursive workstation recipe capture is not allowed");
		capturingRecipe = true;
		try {
			return DynamicBehaviorRegistry.createWorkstationRecipe(definition, workstationContext(), player);
		} finally {
			capturingRecipe = false;
		}
	}

	public int recipeMenuState() {
		if (isWorkstationLocked()) return com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess.GENERATING;
		return currentSignature != null && currentRecipe == null
				? com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess.MAKE_RECIPE_AVAILABLE
				: com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess.EMPTY_OR_VALID;
	}

	@Override
	public WorkstationRecipeStatus recipeStatus() {
		return recipeStatus;
	}

	@Override
	public ItemStack recipeOutput() {
		return currentRecipe == null ? ItemStack.EMPTY : currentRecipe.outputStack();
	}

	@Override
	public JsonObject recipeData() {
		return currentRecipe == null ? new JsonObject() : currentRecipe.recipeData();
	}

	@Override
	public boolean tryCompleteRecipe() {
		return tryCompleteRecipe(Map.of());
	}

	@Override
	public boolean tryCompleteRecipe(Map<String, ItemStack> additionalOutputs) {
		assertRuntimeMutable();
		refreshRecipe();
		if (currentRecipe == null || currentRequest == null || currentSignature == null) return false;
		WorkstationRecipeSignature fresh = WorkstationRecipeSignature.capture(
				workstationDefinition(), currentRequest, this);
		if (!currentSignature.equals(fresh)) return false;
		int outputSlot = firstSlotWithRole(DynamicWorkstationSlotRole.OUTPUT);
		if (outputSlot < 0) return false;
		ItemStack produced = currentRecipe.outputStack();
		if (produced.isEmpty()) return false;

		NonNullList<ItemStack> simulated = NonNullList.withSize(workstationItems.size(), ItemStack.EMPTY);
		for (int index = 0; index < workstationItems.size(); index++) {
			simulated.set(index, workstationItems.get(index).copy());
		}
		for (WorkstationRecipeSignature.Ingredient ingredient : currentSignature.ingredients()) {
			int index = slotIndex(ingredient.slotId());
			if (index < 0) return false;
			ItemStack stack = simulated.get(index);
			if (stack.getCount() < ingredient.count()
					|| !ItemStack.isSameItemSameComponents(
							com.yeyito.littlechemistry.crafting.RecipeIngredient.normalize(stack),
						ingredient.stack())) return false;
				switch (ingredient.use()) {
					case CONSUME -> stack.shrink(ingredient.count());
					case KEEP -> {
				}
				case DAMAGE -> {
					if (!stack.isDamageableItem() || stack.nextDamageWillBreak()) simulated.set(index, ItemStack.EMPTY);
					else stack.setDamageValue(stack.getDamageValue() + 1);
				}
			}
		}
		DynamicWorkstationSlot primaryOutput = slotSpec(outputSlot);
		int primaryCapacity = Math.min(produced.getMaxStackSize(), primaryOutput.maxStack());
		ItemStack target = simulated.get(outputSlot);
		if (target.isEmpty() && produced.getCount() <= primaryCapacity) simulated.set(outputSlot, produced.copy());
		else if (ItemStack.isSameItemSameComponents(target, produced)
				&& target.getCount() + produced.getCount() <= Math.min(target.getMaxStackSize(), primaryOutput.maxStack())) {
			target.grow(produced.getCount());
		} else {
			recipeStatus = WorkstationRecipeStatus.BLOCKED;
			return false;
		}
		for (var additional : additionalOutputs.entrySet()) {
			int index = slotIndex(additional.getKey());
			DynamicWorkstationSlot specification = slotSpec(index);
			if (index < 0 || index == outputSlot || specification == null || !specification.role().isOutput()) {
				throw new IllegalArgumentException("Additional output targets a non-output workstation slot: "
						+ additional.getKey());
			}
			ItemStack value = additional.getValue().copy();
			ItemStack existing = simulated.get(index);
			if (existing.isEmpty()) {
				if (value.getCount() > Math.min(value.getMaxStackSize(), specification.maxStack())) {
					recipeStatus = WorkstationRecipeStatus.BLOCKED;
					return false;
				}
				simulated.set(index, value);
			} else if (ItemStack.isSameItemSameComponents(existing, value)
					&& existing.getCount() + value.getCount() <= Math.min(existing.getMaxStackSize(),
					specification.maxStack())) {
				existing.grow(value.getCount());
			} else {
				recipeStatus = WorkstationRecipeStatus.BLOCKED;
				return false;
			}
		}
		workstationItems = simulated;
		setChanged();
		return true;
	}

	@Override
	public void cancelProcessing() {
		assertRuntimeMutable();
		persistentState.clear();
		DynamicContentDefinition definition = workstationDefinition();
		if (definition != null) ensureWorkstationState(definition.workstation());
		setChanged();
	}

	@Override
	public Set<String> slotIds() {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) return Set.of();
		return definition.workstation().slots().stream().map(DynamicWorkstationSlot::id)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	@Override
	public ItemStack stack(String slotId) {
		int index = slotIndex(slotId);
		return index < 0 ? ItemStack.EMPTY : workstationItems.get(index).copy();
	}

	@Override
	public void setStack(String slotId, ItemStack stack) {
		assertRuntimeMutable();
		int index = slotIndex(slotId);
		if (index < 0) throw new IllegalArgumentException("Unknown workstation slot: " + slotId);
		if (isSlotLocked(index)) throw new IllegalStateException("Workstation slot is locked: " + slotId);
		ItemStack value = stack.copy();
		int capacity = Math.min(slotSpec(index).maxStack(), value.getMaxStackSize());
		if (value.getCount() > capacity) {
			throw new IllegalArgumentException("Stack exceeds workstation slot " + slotId + " capacity of " + capacity);
		}
		workstationItems.set(index, value);
		setChanged();
	}

	@Override
	public long persistentState(String key) {
		return persistentState.getOrDefault(key, 0L);
	}

	@Override
	public void setPersistentState(String key, long value) {
		assertRuntimeMutable();
		if (!validRuntimeKey(key)) throw new IllegalArgumentException("Invalid workstation persistent state key");
		if (!persistentState.containsKey(key) && persistentState.size() >= MAX_PERSISTENT_KEYS) {
			throw new IllegalArgumentException("Workstation persistent state exceeds its key budget");
		}
		if (persistentState.getOrDefault(key, 0L) == value && persistentState.containsKey(key)) return;
		persistentState.put(key, value);
		setChanged();
	}

	public @Nullable String generatedState(String key) {
		if (!validRuntimeKey(key)) throw new IllegalArgumentException("Invalid generated block state key");
		return generatedState.get(key);
	}

	public Map<String, String> generatedStateSnapshot() {
		return Map.copyOf(generatedState);
	}

	public void setGeneratedState(String key, @Nullable String value) {
		assertRuntimeMutable();
		if (!validRuntimeKey(key)) throw new IllegalArgumentException("Invalid generated block state key");
		if (value != null && (value.length() > 1_024 || value.indexOf('\0') >= 0)) {
			throw new IllegalArgumentException("Generated block state values may contain at most 1024 characters");
		}
		if (value != null && !generatedState.containsKey(key) && generatedState.size() >= MAX_PERSISTENT_KEYS) {
			throw new IllegalArgumentException("Generated block state exceeds its key budget");
		}
		String previous = value == null ? generatedState.remove(key) : generatedState.put(key, value);
		if (generatedState.toString().length() > 16_384) {
			if (previous == null) generatedState.remove(key); else generatedState.put(key, previous);
			throw new IllegalArgumentException("Generated block state exceeds its encoded size budget");
		}
		setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, 3);
		}
	}

	@Override
	public int uiState(String channelId) {
		DynamicContentDefinition definition = workstationDefinition();
		DynamicWorkstationStateChannel channel = definition == null ? null
				: definition.workstation().ui().stateChannel(channelId);
		if (channel == null) throw new IllegalArgumentException("Unknown workstation UI channel: " + channelId);
		return uiState.getOrDefault(channelId, channel.initialValue());
	}

	@Override
	public void setUiState(String channelId, int value) {
		assertRuntimeMutable();
		DynamicContentDefinition definition = workstationDefinition();
		DynamicWorkstationStateChannel channel = definition == null ? null
				: definition.workstation().ui().stateChannel(channelId);
		if (channel == null) throw new IllegalArgumentException("Unknown workstation UI channel: " + channelId);
		if (value < channel.minimum() || value > channel.maximum()) {
			throw new IllegalArgumentException("Workstation UI channel value is outside its declared range");
		}
		if (uiState.getOrDefault(channelId, channel.initialValue()) == value) return;
		uiState.put(channelId, value);
		setChanged();
	}

	public boolean isSlotLocked(int slot) {
		if (level instanceof ServerLevel serverLevel) {
			AiCraftingManager manager = AiCraftingManager.active();
			return manager != null && manager.isWorkstationSlotLocked(serverLevel, worldPosition, slot);
		}
		return false;
	}

	public long lockedSlotMask() {
		long mask = 0L;
		Set<Integer> slots = Set.of();
		if (level instanceof ServerLevel serverLevel) {
			AiCraftingManager manager = AiCraftingManager.active();
			if (manager != null) slots = manager.workstationLockedSlotIndexes(serverLevel, worldPosition);
		}
		for (int slot : slots) {
			if (slot >= 0 && slot < Long.SIZE) mask |= 1L << slot;
		}
		return mask;
	}

	public boolean mayUseSlot(int slot, ItemStack stack, WorkstationSlotAction action,
			@Nullable ServerPlayer player, @Nullable Direction automationSide) {
		DynamicContentDefinition definition = workstationDefinition();
		DynamicWorkstationSlot specification = slotSpec(slot);
		if (definition == null || specification == null || isSlotLocked(slot)) return false;
		boolean fallback = action == WorkstationSlotAction.INSERT
				? specification.allowPlayerInsert() : specification.allowPlayerExtract();
		if (evaluatingSlotRule) return false;
		evaluatingSlotRule = true;
		try {
			if (player != null) {
				return DynamicBehaviorRegistry.canUseWorkstationSlot(definition, workstationContext(), player,
						specification.id(), stack, action, fallback);
			}
			return DynamicBehaviorRegistry.canAutomateWorkstationSlot(definition, workstationContext(),
					specification.id(), stack, action, automationSide, fallback);
		} finally {
			evaluatingSlotRule = false;
		}
	}

	public boolean customButton(ServerPlayer player, String buttonId) {
		DynamicContentDefinition definition = workstationDefinition();
		return definition != null && DynamicBehaviorRegistry.workstationButtonPressed(
				definition, workstationContext(), player, buttonId);
	}

	private void ensureWorkstationState(DynamicWorkstationSpec specification) {
		boolean changed = false;
		for (DynamicWorkstationStateChannel channel : specification.ui().stateChannels()) {
			Integer value = uiState.get(channel.id());
			if (value == null || value < channel.minimum() || value > channel.maximum()) {
				uiState.put(channel.id(), channel.initialValue());
				changed = true;
			}
		}
		int oldUiSize = uiState.size();
		uiState.keySet().removeIf(id -> specification.ui().stateChannel(id) == null);
		changed |= oldUiSize != uiState.size();
		int oldPersistentSize = persistentState.size();
		persistentState.keySet().removeIf(key -> !validRuntimeKey(key));
		changed |= oldPersistentSize != persistentState.size();

		if (level instanceof ServerLevel serverLevel) {
			for (int index = 0; index < workstationItems.size(); index++) {
				ItemStack stack = workstationItems.get(index);
				if (stack.isEmpty()) continue;
				Identifier dynamicId = stack.get(DynamicContentObjects.CONTENT_ID);
				if (unavailableDynamicId(dynamicId)) {
					workstationItems.set(index, ItemStack.EMPTY);
					changed = true;
					continue;
				}
				if (index >= specification.slots().size()) {
					net.minecraft.world.Containers.dropItemStack(serverLevel, worldPosition.getX() + 0.5,
							worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
					workstationItems.set(index, ItemStack.EMPTY);
					changed = true;
					continue;
				}
				int capacity = Math.min(stack.getMaxStackSize(), specification.slots().get(index).maxStack());
				if (stack.getCount() > capacity) {
					ItemStack excess = stack.copyWithCount(stack.getCount() - capacity);
					stack.setCount(capacity);
					net.minecraft.world.Containers.dropItemStack(serverLevel, worldPosition.getX() + 0.5,
							worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, excess);
					changed = true;
				}
			}
		}
		if (changed) {
			recipeDirty = true;
			super.setChanged();
		}
	}

	private void removeUnavailableDynamicStacks() {
		boolean changed = false;
		for (int index = 0; index < workstationItems.size(); index++) {
			if (unavailableDynamicId(workstationItems.get(index).get(DynamicContentObjects.CONTENT_ID))) {
				workstationItems.set(index, ItemStack.EMPTY);
				changed = true;
			}
		}
		if (changed) {
			recipeDirty = true;
			super.setChanged();
		}
	}

	private static boolean unavailableDynamicId(@Nullable Identifier id) {
		return id != null && LittleChemistry.MOD_ID.equals(id.getNamespace()) && DynamicContentCatalog.find(id) == null;
	}

	private static boolean validRuntimeKey(String key) {
		return key != null && key.matches("[a-z][a-z0-9_]{0,63}");
	}

	private int slotIndex(String id) {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) return -1;
		List<DynamicWorkstationSlot> slots = definition.workstation().slots();
		for (int index = 0; index < slots.size(); index++) if (slots.get(index).id().equals(id)) return index;
		return -1;
	}

	private DynamicWorkstationSlot slotSpec(int index) {
		DynamicContentDefinition definition = workstationDefinition();
		return definition == null || index < 0 || index >= definition.workstation().slots().size()
				? null : definition.workstation().slots().get(index);
	}

	private int firstSlotWithRole(DynamicWorkstationSlotRole role) {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) return -1;
		for (int index = 0; index < definition.workstation().slots().size(); index++) {
			if (definition.workstation().slots().get(index).role() == role) return index;
		}
		return -1;
	}

	@Override
	public int getContainerSize() {
		return MAX_SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : workstationItems) if (!stack.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < workstationItems.size() ? workstationItems.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		if (isSlotLocked(slot)) return ItemStack.EMPTY;
		ItemStack removed = ContainerHelper.removeItem(workstationItems, slot, count);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (isSlotLocked(slot)) return ItemStack.EMPTY;
		return ContainerHelper.takeItem(workstationItems, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		assertRuntimeMutable();
		if (slot < 0 || slot >= workstationItems.size() || isSlotLocked(slot)) return;
		ItemStack value = stack.copy();
		DynamicWorkstationSlot specification = slotSpec(slot);
		if (specification == null) return;
		int capacity = Math.min(specification.maxStack(), value.getMaxStackSize());
		if (value.getCount() > capacity) return;
		workstationItems.set(slot, value);
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return canAcceptAutomationStack(slot, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return isValidWorkstation(player);
	}

	@Override
	public void clearContent() {
		if (isWorkstationLocked()) return;
		workstationItems.clear();
		setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) return new int[0];
		int[] slots = new int[definition.workstation().slots().size()];
		for (int index = 0; index < slots.length; index++) slots[index] = index;
		return slots;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return canAcceptAutomationStack(slot, stack)
				&& mayUseSlot(slot, stack, WorkstationSlotAction.INSERT, null, direction);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return mayUseSlot(slot, stack, WorkstationSlotAction.EXTRACT, null, direction);
	}

	/** Basic capacity/identity check shared by vanilla hopper and custom Fabric transfer automation. */
	public boolean canAcceptAutomationStack(int slot, ItemStack stack) {
		DynamicWorkstationSlot specification = slotSpec(slot);
		if (specification == null || stack == null || stack.isEmpty() || isSlotLocked(slot)) return false;
		ItemStack existing = getItem(slot);
		if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return false;
		int capacity = Math.min(specification.maxStack(), stack.getMaxStackSize());
		return stack.getCount() <= capacity - existing.getCount();
	}

	public NonNullList<ItemStack> drainWorkstationItems() {
		NonNullList<ItemStack> drained = NonNullList.withSize(workstationItems.size(), ItemStack.EMPTY);
		for (int index = 0; index < workstationItems.size(); index++) {
			drained.set(index, workstationItems.get(index));
			workstationItems.set(index, ItemStack.EMPTY);
		}
		setChanged();
		return drained;
	}

	@Override
	public Component getDisplayName() {
		DynamicContentDefinition definition = workstationDefinition();
		return definition == null ? Component.translatable("container.little_chemistry.workstation")
				: DynamicContentObjects.displayName(definition);
	}

	@Override
	public WorkstationOpenData getScreenOpeningData(ServerPlayer player) {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null) throw new IllegalStateException("Cannot open a non-workstation dynamic block");
		return new WorkstationOpenData(worldPosition, LittleChemistry.id(definition.name()), definition.workstation());
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		DynamicContentDefinition definition = workstationDefinition();
		return definition == null ? null
				: new DynamicWorkstationMenu(containerId, inventory, this, definition.workstation());
	}

	public static int purgeLoadedInventoryReferences(net.minecraft.server.MinecraftServer server, Set<String> names) {
		List<DynamicBlockEntity> snapshot;
		synchronized (LIVE) {
			snapshot = new ArrayList<>(LIVE);
		}
		int removed = 0;
		for (DynamicBlockEntity blockEntity : snapshot) {
			if (!(blockEntity.level instanceof ServerLevel serverLevel) || serverLevel.getServer() != server) continue;
			boolean changed = false;
			for (int slot = 0; slot < blockEntity.workstationItems.size(); slot++) {
				ItemStack stack = blockEntity.workstationItems.get(slot);
				Identifier id = stack.get(DynamicContentObjects.CONTENT_ID);
				if (id != null && LittleChemistry.MOD_ID.equals(id.getNamespace()) && names.contains(id.getPath())) {
					blockEntity.workstationItems.set(slot, ItemStack.EMPTY);
					removed++;
					changed = true;
				}
			}
			if (changed) blockEntity.setChanged();
		}
		return removed;
	}

	public static int removeLoaded(net.minecraft.server.MinecraftServer server, Set<String> names) {
		List<DynamicBlockEntity> snapshot;
		synchronized (LIVE) {
			snapshot = new ArrayList<>(LIVE);
		}
		int removed = 0;
		for (DynamicBlockEntity blockEntity : snapshot) {
			if (!(blockEntity.level instanceof ServerLevel serverLevel)
					|| serverLevel.getServer() != server || blockEntity.contentId == null
					|| !LittleChemistry.MOD_ID.equals(blockEntity.contentId.getNamespace())
					|| !names.contains(blockEntity.contentId.getPath())) continue;
			if (!blockEntity.isEmpty()) {
				net.minecraft.world.Containers.dropContents(serverLevel, blockEntity.worldPosition,
						blockEntity.drainWorkstationItems());
			}
			if (serverLevel.removeBlock(blockEntity.worldPosition, false)) removed++;
		}
		return removed;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		synchronized (LIVE) {
			LIVE.remove(this);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		synchronized (LIVE) {
			LIVE.add(this);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		contentId = input.read("content_id", Identifier.CODEC).orElse(null);
		workstationItems = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, workstationItems);
		persistentState.clear();
		persistentState.putAll(input.read("workstation_state", LONG_STATE_CODEC).orElse(Map.of()));
		if (persistentState.size() > MAX_PERSISTENT_KEYS) persistentState.clear();
		uiState.clear();
		uiState.putAll(input.read("workstation_ui", UI_STATE_CODEC).orElse(Map.of()));
		if (uiState.size() > DynamicWorkstationUi.MAX_STATE_CHANNELS) uiState.clear();
		generatedState.clear();
		generatedState.putAll(input.read("generated_state", GENERATED_STATE_CODEC).orElse(Map.of()));
		if (generatedState.size() > MAX_PERSISTENT_KEYS || generatedState.toString().length() > 16_384) {
			generatedState.clear();
		}
		currentRequest = null;
		currentSignature = null;
		currentRecipe = null;
		recipeStatus = WorkstationRecipeStatus.NONE;
		recipeDirty = true;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("content_id", Identifier.CODEC, contentId);
		if (!isEmpty()) ContainerHelper.saveAllItems(output, workstationItems);
		if (!persistentState.isEmpty()) output.store("workstation_state", LONG_STATE_CODEC, Map.copyOf(persistentState));
		if (!uiState.isEmpty()) output.store("workstation_ui", UI_STATE_CODEC, Map.copyOf(uiState));
		if (!generatedState.isEmpty()) output.store("generated_state", GENERATED_STATE_CODEC, Map.copyOf(generatedState));
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		contentId = components.get(DynamicContentObjects.CONTENT_ID);
		setChanged();
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (contentId != null) builder.set(DynamicContentObjects.CONTENT_ID, contentId);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
		CompoundTag tag = new CompoundTag();
		if (contentId != null) tag.putString("content_id", contentId.toString());
		appendGeneratedStateUpdate(tag, generatedState);
		return tag;
	}

	/** Writes the same bounded map shape consumed by {@link #loadAdditional(ValueInput)} on tracking clients. */
	static void appendGeneratedStateUpdate(CompoundTag tag, Map<String, String> state) {
		if (state.isEmpty()) return;
		CompoundTag encoded = new CompoundTag();
		state.forEach(encoded::putString);
		tag.put("generated_state", encoded);
	}

	@Override
	public void setChanged() {
		assertRuntimeMutable();
		recipeDirty = true;
		super.setChanged();
	}

	private void assertRuntimeMutable() {
		if (capturingRecipe || evaluatingSlotRule) {
			throw new IllegalStateException("Workstation recipe capture and slot-rule callbacks must be read-only");
		}
	}
}
