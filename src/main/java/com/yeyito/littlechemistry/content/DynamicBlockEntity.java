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
import com.yeyito.littlechemistry.particle.DynamicParticles;
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
import net.minecraft.world.entity.ContainerUser;
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
	private static final int INVENTION_PARTICLE_INTERVAL_TICKS = 8;
	private static final int MAX_PERSISTENT_KEYS = 64;
	private static final Codec<Map<String, Long>> LONG_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.LONG);
	private static final Codec<Map<String, Integer>> UI_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.INT);
	private static final Codec<Map<String, String>> GENERATED_STATE_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.STRING);
	private static final Set<DynamicBlockEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private Identifier contentId;
	/** North-authored local assembly coordinate; zero also represents every legacy single-cell block. */
	private BlockPos assemblyOffset = BlockPos.ZERO;
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
	private int storageOpeners;
	private @Nullable String transientVisualState;
	private boolean assemblyStateNeedsReconcile;

	public DynamicBlockEntity(BlockPos position, BlockState state) {
		super(DynamicContentObjects.BLOCK_ENTITY_TYPE, position, state);
		synchronized (LIVE) {
			LIVE.add(this);
		}
	}

	public Identifier contentId() {
		return contentId;
	}

	public BlockPos assemblyOffset() { return assemblyOffset; }

	void initializeAssembly(Identifier contentId, BlockPos assemblyOffset) {
		if (contentId == null || assemblyOffset == null) throw new IllegalArgumentException("Assembly identity is required");
		this.contentId = contentId;
		this.assemblyOffset = assemblyOffset.immutable();
		super.setChanged();
	}

	public DynamicContentDefinition workstationDefinition() {
		DynamicContentDefinition definition = contentId == null ? null : DynamicContentCatalog.find(contentId);
		return definition != null && definition.workstation() != null ? definition : null;
	}

	public boolean isWorkstation() {
		return workstationDefinition() != null;
	}

	public DynamicContentDefinition storageDefinition() {
		DynamicContentDefinition definition = contentId == null ? null : DynamicContentCatalog.find(contentId);
		return definition != null && definition.storage() != null ? definition : null;
	}

	public boolean isStorage() { return storageDefinition() != null; }

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
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.isWorkstationLocked();
		if (!assemblyOffset.equals(BlockPos.ZERO)) return true;
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
		if (!entity.assemblyOffset.equals(BlockPos.ZERO)) return;
		if (entity.assemblyStateNeedsReconcile) {
			entity.assemblyStateNeedsReconcile = false;
			DynamicBlockAssemblyRuntime.setVariant(level, entity, entity.generatedState.get("geometry"));
			DynamicBlockAssemblyRuntime.synchronizeVisualState(level, entity, entity.generatedState.get("visual"));
		}
		DynamicContentDefinition definition = entity.workstationDefinition();
		if (definition == null) return;
		entity.ensureWorkstationState(definition.workstation());
		entity.refreshRecipe();
		entity.updateAutomaticWorkstationVisual();
		entity.emitInventionLifecycleParticles(definition);
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
			recipeStatus = currentRecipe == null ? WorkstationRecipeStatus.NONE
					: currentRecipe.isRejected() ? WorkstationRecipeStatus.REJECTED : WorkstationRecipeStatus.READY;
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

	/** Returns the virtual barrier preview shown only in the primary output menu slot for a rejected recipe. */
	public ItemStack rejectionDisplayStack(int slot) {
		if (slot < 0 || slot >= workstationItems.size()
				|| currentRecipe == null || !currentRecipe.isRejected() || !workstationItems.get(slot).isEmpty()
				|| slot != firstSlotWithRole(DynamicWorkstationSlotRole.OUTPUT)) return ItemStack.EMPTY;
		return currentRecipe.outputStack();
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
		if (currentRecipe == null || currentRecipe.isRejected()
				|| currentRequest == null || currentSignature == null) return false;
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
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.stack(slotId);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return ItemStack.EMPTY;
		int index = slotIndex(slotId);
		return index < 0 ? ItemStack.EMPTY : workstationItems.get(index).copy();
	}

	@Override
	public void setStack(String slotId, ItemStack stack) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) {
			root.setStack(slotId, stack);
			return;
		}
		if (!assemblyOffset.equals(BlockPos.ZERO)) return;
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

	public Map<String, String> generatedStateSnapshot() { return Map.copyOf(generatedState); }

	public @Nullable String visualState() {
		return transientVisualState == null ? generatedState.get("visual") : transientVisualState;
	}

	public void setGeneratedState(String key, @Nullable String value) {
		if (level != null && !assemblyOffset.equals(BlockPos.ZERO)) {
			DynamicBlockEntity root = DynamicBlockAssemblyRuntime.rootEntity(level, worldPosition);
			if (root == null) throw new IllegalStateException("Generated block assembly root is unavailable");
			root.setGeneratedState(key, value);
			return;
		}
		assertRuntimeMutable();
		if (!validRuntimeKey(key)) throw new IllegalArgumentException("Invalid generated block state key");
		if (key.equals("geometry") && value != null) {
			DynamicContentDefinition definition = contentId == null ? null : DynamicContentCatalog.find(contentId);
			DynamicBlockAssembly assembly = definition == null || definition.block() == null
					? null : definition.block().assembly();
			if (assembly == null || assembly.variantIndex(value) < 0) {
				throw new IllegalArgumentException("Unknown generated block assembly geometry variant: " + value);
			}
		}
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
		if (java.util.Objects.equals(previous, value)) return;
		super.setChanged();
		if (level != null) {
			if (key.equals("geometry")) DynamicBlockAssemblyRuntime.setVariant(level, this, value);
			if (key.equals("visual")) DynamicBlockAssemblyRuntime.synchronizeVisualState(level, this, value);
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	void setMirroredVisualState(@Nullable String value) {
		if (value == null) generatedState.remove("visual"); else generatedState.put("visual", value);
		super.setChanged();
	}

	void setTransientVisualState(@Nullable String value) {
		transientVisualState = value;
		super.setChanged();
	}

	private void updateAutomaticWorkstationVisual() {
		DynamicContentDefinition definition = workstationDefinition();
		if (definition == null || definition.blockModel() == null) return;
		boolean hasActive = definition.blockModel().referencedTextureIds().stream()
				.anyMatch(id -> definition.blockModel().findTexture(id + "_active") != null);
		if (!hasActive) return;
		String currentVisual = generatedState.get("visual");
		if (currentVisual != null && !currentVisual.equals("active")) return;
		boolean active = !isEmpty() || recipeStatus == WorkstationRecipeStatus.GENERATING
				|| recipeStatus == WorkstationRecipeStatus.READY || recipeStatus == WorkstationRecipeStatus.BLOCKED;
		setGeneratedState("visual", active ? "active" : null);
	}

	/** Mirrors the native AI crafting-table cadence while leaving the particle artwork declarative per workstation. */
	private void emitInventionLifecycleParticles(DynamicContentDefinition definition) {
		if (!(level instanceof ServerLevel serverLevel)
				|| Math.floorMod(serverLevel.getGameTime() + worldPosition.asLong(),
				INVENTION_PARTICLE_INTERVAL_TICKS) != 0) return;
		DynamicWorkstationParticleEffect effect;
		if (isWorkstationLocked()) {
			effect = definition.workstation().particles().inventing();
		} else {
			int output = firstSlotWithRole(DynamicWorkstationSlotRole.OUTPUT);
			if (output < 0 || workstationItems.get(output).isEmpty()) return;
			effect = definition.workstation().particles().ready();
		}
		double x = worldPosition.getX() + 0.5;
		double y = worldPosition.getY() + 1.08;
		double z = worldPosition.getZ() + 0.5;
		if (effect.custom()) {
			DynamicParticles.spawn(serverLevel, definition, effect.customParticleId(), x, y, z, effect.count(),
					effect.horizontalSpread(), effect.verticalSpread(), effect.horizontalSpread(), effect.speed());
		} else {
			serverLevel.sendParticles(effect.type().particle(), x, y, z, effect.count(),
					effect.horizontalSpread(), effect.verticalSpread(), effect.horizontalSpread(), effect.speed());
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
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.isSlotLocked(slot);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return true;
		if (level instanceof ServerLevel serverLevel) {
			AiCraftingManager manager = AiCraftingManager.active();
			return manager != null && manager.isWorkstationSlotLocked(serverLevel, worldPosition, slot);
		}
		return false;
	}

	public long lockedSlotMask() {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.lockedSlotMask();
		if (!assemblyOffset.equals(BlockPos.ZERO)) return -1L;
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
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.mayUseSlot(slot, stack, action, player, automationSide);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
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

	private @Nullable DynamicBlockEntity inventoryRoot() {
		if (assemblyOffset.equals(BlockPos.ZERO)) return this;
		return level == null ? null : DynamicBlockAssemblyRuntime.rootEntity(level, worldPosition);
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
		DynamicContentDefinition storage = storageDefinition();
		return storage == null ? MAX_SLOTS : storage.storage().slots();
	}

	@Override
	public boolean isEmpty() {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.isEmpty();
		if (!assemblyOffset.equals(BlockPos.ZERO)) return true;
		for (ItemStack stack : workstationItems) if (!stack.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.getItem(slot);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return ItemStack.EMPTY;
		return slot >= 0 && slot < getContainerSize() ? workstationItems.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.removeItem(slot, count);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return ItemStack.EMPTY;
		if (isSlotLocked(slot)) return ItemStack.EMPTY;
		ItemStack removed = ContainerHelper.removeItem(workstationItems, slot, count);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.removeItemNoUpdate(slot);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return ItemStack.EMPTY;
		if (isSlotLocked(slot)) return ItemStack.EMPTY;
		return ContainerHelper.takeItem(workstationItems, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) {
			root.setItem(slot, stack);
			return;
		}
		if (!assemblyOffset.equals(BlockPos.ZERO)) return;
		assertRuntimeMutable();
		if (slot < 0 || slot >= getContainerSize() || isSlotLocked(slot)) return;
		ItemStack value = stack.copy();
		if (isStorage()) {
			if (!canStoreInStorage(value)) return;
			value.limitSize(value.getMaxStackSize());
			workstationItems.set(slot, value);
			setChanged();
			return;
		}
		DynamicWorkstationSlot specification = slotSpec(slot);
		if (specification == null) return;
		int capacity = Math.min(specification.maxStack(), value.getMaxStackSize());
		if (value.getCount() > capacity) return;
		workstationItems.set(slot, value);
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.canPlaceItem(slot, stack);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
		return isStorage()
				? slot >= 0 && slot < getContainerSize() && canStoreInStorage(stack)
				: canAcceptAutomationStack(slot, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.stillValid(player);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
		return (isWorkstation() || isStorage()) && level != null && level.getBlockEntity(worldPosition) == this
				&& Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) {
			root.clearContent();
			return;
		}
		if (!assemblyOffset.equals(BlockPos.ZERO)) return;
		if (isWorkstationLocked()) return;
		workstationItems.clear();
		setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		DynamicContentDefinition definition = workstationDefinition();
		int count = definition == null ? isStorage() ? getContainerSize() : 0 : definition.workstation().slots().size();
		int[] slots = new int[count];
		for (int index = 0; index < slots.length; index++) slots[index] = index;
		return slots;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.canPlaceItemThroughFace(slot, stack, direction);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
		if (isStorage()) return canPlaceItem(slot, stack);
		return canAcceptAutomationStack(slot, stack)
				&& mayUseSlot(slot, stack, WorkstationSlotAction.INSERT, null, direction);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.canTakeItemThroughFace(slot, stack, direction);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
		if (isStorage()) return slot >= 0 && slot < getContainerSize();
		return mayUseSlot(slot, stack, WorkstationSlotAction.EXTRACT, null, direction);
	}

	/** Basic capacity/identity check shared by vanilla hopper and custom Fabric transfer automation. */
	public boolean canAcceptAutomationStack(int slot, ItemStack stack) {
		DynamicBlockEntity root = inventoryRoot();
		if (root != null && root != this) return root.canAcceptAutomationStack(slot, stack);
		if (!assemblyOffset.equals(BlockPos.ZERO)) return false;
		DynamicWorkstationSlot specification = slotSpec(slot);
		if (specification == null || stack == null || stack.isEmpty() || isSlotLocked(slot)) return false;
		ItemStack existing = getItem(slot);
		if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return false;
		int capacity = Math.min(specification.maxStack(), stack.getMaxStackSize());
		return stack.getCount() <= capacity - existing.getCount();
	}

	private boolean canStoreInStorage(ItemStack stack) {
		DynamicContentDefinition definition = storageDefinition();
		return stack.isEmpty() || definition != null && (definition.storage().acceptsContainerItems()
				|| !stack.has(net.minecraft.core.component.DataComponents.CONTAINER));
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
		if (definition == null) definition = storageDefinition();
		return definition == null ? Component.translatable("container.little_chemistry.workstation")
					: DynamicContentObjects.displayName(definition);
	}

	@Override
	public void startOpen(ContainerUser player) {
		if (isStorage() && storageOpeners++ == 0) syncTransientStorageVisual();
	}

	@Override
	public void stopOpen(ContainerUser player) {
		if (isStorage() && storageOpeners > 0 && --storageOpeners == 0) syncTransientStorageVisual();
	}

	private void syncTransientStorageVisual() {
		if (level != null) {
			DynamicContentDefinition definition = storageDefinition();
			if (definition != null && definition.block() != null && definition.block().assembly() != null) {
				if (definition.block().assembly().variantIndex("open") >= 0) {
					DynamicBlockAssemblyRuntime.setVariant(level, this,
							storageOpeners > 0 ? "open" : generatedState.get("geometry"));
				}
				DynamicBlockAssemblyRuntime.synchronizeTransientVisualState(
						level, this, storageOpeners > 0 ? "open" : null);
			}
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
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
					|| !blockEntity.assemblyOffset.equals(BlockPos.ZERO)
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
	public void preRemoveSideEffects(BlockPos position, BlockState state) {
		DynamicBlockEntity root = level == null ? null : DynamicBlockAssemblyRuntime.rootEntity(level, position);
		DynamicContentDefinition definition = root == null || root.contentId == null
				? null : DynamicContentCatalog.find(root.contentId);
		if (level != null && root != null && definition != null && definition.block() != null
				&& definition.block().assembly() != null && DynamicBlockAssemblyRuntime.beginRemoval()) {
			try {
				DynamicBlockAssemblyRuntime.removeOtherMembers(level, root, position);
			} finally {
				DynamicBlockAssemblyRuntime.endRemoval();
			}
		}
		if (assemblyOffset.equals(BlockPos.ZERO)) {
			super.preRemoveSideEffects(position, state);
		} else {
			// Recover any inventory written by an older/broken companion implementation without delegating into the root.
			for (int slot = 0; slot < workstationItems.size(); slot++) {
				ItemStack stack = workstationItems.get(slot);
				if (!stack.isEmpty() && level != null) {
					net.minecraft.world.Containers.dropItemStack(
							level, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, stack);
					workstationItems.set(slot, ItemStack.EMPTY);
				}
			}
		}
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
		assemblyOffset = new BlockPos(
				input.read("assembly_x", Codec.INT).orElse(0),
				input.read("assembly_y", Codec.INT).orElse(0),
				input.read("assembly_z", Codec.INT).orElse(0));
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
		generatedState.entrySet().removeIf(entry -> !validRuntimeKey(entry.getKey())
				|| entry.getValue() == null || entry.getValue().length() > 1_024
				|| entry.getValue().indexOf('\0') >= 0);
		if (generatedState.size() > MAX_PERSISTENT_KEYS || generatedState.toString().length() > 16_384) {
			generatedState.clear();
		}
		storageOpeners = 0;
		transientVisualState = input.read("transient_visual", Codec.STRING).orElse(null);
		assemblyStateNeedsReconcile = true;
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
		if (!assemblyOffset.equals(BlockPos.ZERO)) {
			output.store("assembly_x", Codec.INT, assemblyOffset.getX());
			output.store("assembly_y", Codec.INT, assemblyOffset.getY());
			output.store("assembly_z", Codec.INT, assemblyOffset.getZ());
		}
		if (!isEmpty()) ContainerHelper.saveAllItems(output, workstationItems);
		if (!persistentState.isEmpty()) output.store("workstation_state", LONG_STATE_CODEC, Map.copyOf(persistentState));
		if (!uiState.isEmpty()) output.store("workstation_ui", UI_STATE_CODEC, Map.copyOf(uiState));
		if (!generatedState.isEmpty()) output.store("generated_state", GENERATED_STATE_CODEC, Map.copyOf(generatedState));
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		contentId = components.get(DynamicContentObjects.CONTENT_ID);
		assemblyOffset = BlockPos.ZERO;
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
		if (transientVisualState != null) tag.putString("transient_visual", transientVisualState);
		if (!assemblyOffset.equals(BlockPos.ZERO)) {
			tag.putInt("assembly_x", assemblyOffset.getX());
			tag.putInt("assembly_y", assemblyOffset.getY());
			tag.putInt("assembly_z", assemblyOffset.getZ());
		}
		if (!generatedState.isEmpty() || storageOpeners > 0) {
			CompoundTag state = new CompoundTag();
			generatedState.forEach(state::putString);
			if (storageOpeners > 0 && isStorage()) state.putString("visual", "open");
			tag.put("generated_state", state);
		}
		return tag;
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
