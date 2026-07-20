package com.yeyito.littlechemistry.behavior;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.Map;

/**
 * Engine bridge behind {@link DynamicWorkstationContext}.
 *
 * <p>The generic workstation block entity/runtime implements this interface. Generated source should normally use
 * the context facade instead. Stack and JSON values crossing this boundary are treated as snapshots. The runtime is
 * responsible for bounding the number of persistent keys and synchronized UI channels according to the validated
 * workstation definition.</p>
 */
public interface DynamicWorkstationRuntimeAccess {
	Set<String> slotIds();

	ItemStack stack(String slotId);

	void setStack(String slotId, ItemStack stack);

	long persistentState(String key);

	void setPersistentState(String key, long value);

	int uiState(String channelId);

	void setUiState(String channelId, int value);

	default WorkstationRecipeStatus recipeStatus() {
		return WorkstationRecipeStatus.NONE;
	}

	default ItemStack recipeOutput() {
		return ItemStack.EMPTY;
	}

	default JsonObject recipeData() {
		return new JsonObject();
	}

	/**
	 * Atomically verifies and applies the captured ingredient uses and inserts the generated result.
	 *
	 * @return whether the whole transaction committed
	 */
	default boolean tryCompleteRecipe() {
		return tryCompleteRecipe(Map.of());
	}

	/** Atomic completion with optional existing-item output/byproduct stacks keyed by named output slots. */
	default boolean tryCompleteRecipe(Map<String, ItemStack> additionalOutputs) {
		return false;
	}

	/** Cancels placement-local processing state without deleting a globally cached recipe. */
	default void cancelProcessing() {
	}

	void setChanged();
}
