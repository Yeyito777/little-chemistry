package com.yeyito.littlechemistry.behavior;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Direct, server-only facade supplied to generated workstation callbacks.
 *
 * <p>Inventory reads return copies. Use {@link #setStack(String, ItemStack)} for deliberate custom mutation, or
 * prefer {@link #tryCompleteRecipe()} for normal recipe completion so input use and output insertion remain atomic.</p>
 */
public final class DynamicWorkstationContext {
	private static final int MAX_KEY_LENGTH = 64;

	private final ServerLevel level;
	private final BlockPos position;
	private final BlockState blockState;
	private final DynamicContentDefinition definition;
	private final DynamicWorkstationRuntimeAccess runtime;

	public DynamicWorkstationContext(ServerLevel level, BlockPos position, BlockState blockState,
			DynamicContentDefinition definition, DynamicWorkstationRuntimeAccess runtime) {
		this.level = Objects.requireNonNull(level, "level");
		this.position = Objects.requireNonNull(position, "position").immutable();
		this.blockState = Objects.requireNonNull(blockState, "blockState");
		this.definition = Objects.requireNonNull(definition, "definition");
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	public ServerLevel level() {
		return level;
	}

	public BlockPos position() {
		return position;
	}

	public BlockState blockState() {
		return blockState;
	}

	public DynamicContentDefinition definition() {
		return definition;
	}

	public DynamicActionInput actionInput(net.minecraft.server.level.ServerPlayer player) {
		return DynamicActionInput.capture(player);
	}

	public Set<String> slotIds() {
		Set<String> result = new LinkedHashSet<>();
		for (String slotId : runtime.slotIds()) {
			result.add(requireId(slotId, "slot ID"));
		}
		return Collections.unmodifiableSet(result);
	}

	public ItemStack stack(String slotId) {
		ItemStack stack = Objects.requireNonNull(runtime.stack(requireId(slotId, "slot ID")),
				"runtime stack");
		return stack.copy();
	}

	/** Direct custom inventory mutation. Normal generated processing should use {@link #tryCompleteRecipe()}. */
	public void setStack(String slotId, ItemStack stack) {
		runtime.setStack(requireId(slotId, "slot ID"), Objects.requireNonNull(stack, "stack").copy());
	}

	public long persistentState(String key) {
		return runtime.persistentState(requireId(key, "persistent state key"));
	}

	public void setPersistentState(String key, long value) {
		runtime.setPersistentState(requireId(key, "persistent state key"), value);
	}

	public int uiState(String channelId) {
		return runtime.uiState(requireId(channelId, "UI channel ID"));
	}

	public void setUiState(String channelId, int value) {
		runtime.setUiState(requireId(channelId, "UI channel ID"), value);
	}

	public WorkstationRecipeStatus recipeStatus() {
		return Objects.requireNonNull(runtime.recipeStatus(), "recipe status");
	}

	public ItemStack recipeOutput() {
		return Objects.requireNonNull(runtime.recipeOutput(), "recipe output").copy();
	}

	/** Returns a defensive copy of validated per-recipe data, or an empty object when no recipe is ready. */
	public JsonObject recipeData() {
		return Objects.requireNonNull(runtime.recipeData(), "recipe data").deepCopy();
	}

	public boolean tryCompleteRecipe() {
		return runtime.tryCompleteRecipe();
	}

	/**
	 * Atomically completes the generated primary result together with bounded existing-item byproducts or secondary
	 * outputs. Keys must identify declarative output/byproduct slots.
	 */
	public boolean tryCompleteRecipe(Map<String, ItemStack> additionalOutputs) {
		Objects.requireNonNull(additionalOutputs, "additional outputs");
		if (additionalOutputs.size() > 16) throw new IllegalArgumentException("Too many workstation byproducts");
		Map<String, ItemStack> copied = new LinkedHashMap<>();
		for (var entry : additionalOutputs.entrySet()) {
			String slotId = requireId(entry.getKey(), "additional output slot ID");
			ItemStack stack = Objects.requireNonNull(entry.getValue(), "additional output stack").copy();
			if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
				throw new IllegalArgumentException("Additional workstation outputs must be nonempty valid stacks");
			}
			copied.put(slotId, stack);
		}
		return runtime.tryCompleteRecipe(Map.copyOf(copied));
	}

	public void cancelProcessing() {
		runtime.cancelProcessing();
	}

	public void setChanged() {
		runtime.setChanged();
	}

	static String requireId(String value, String description) {
		Objects.requireNonNull(value, description);
		if (value.isEmpty() || value.length() > MAX_KEY_LENGTH || value.charAt(0) < 'a' || value.charAt(0) > 'z') {
			throw new IllegalArgumentException(description + " must be a lower-case local ID no longer than "
					+ MAX_KEY_LENGTH + " characters");
		}
		for (int index = 1; index < value.length(); index++) {
			char character = value.charAt(index);
			if ((character < 'a' || character > 'z') && (character < '0' || character > '9')
					&& character != '_') {
				throw new IllegalArgumentException(description + " must contain only lower-case letters, digits, and '_'");
			}
		}
		return value;
	}
}
