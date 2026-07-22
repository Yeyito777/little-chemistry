package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Persistent and network-synchronized bounded state attached to one generated item stack. */
public final class DynamicItemState {
	private final ItemStack stack;

	private DynamicItemState(ItemStack stack) {
		this.stack = Objects.requireNonNull(stack, "stack");
	}

	public static DynamicItemState of(ItemStack stack) {
		return new DynamicItemState(stack);
	}

	public String getString(String key, String fallback) {
		return load().getString(key, fallback);
	}

	public void setString(String key, String value) {
		mutate(state -> state.setString(key, value));
	}

	public int getInt(String key, int fallback) {
		return load().getInt(key, fallback);
	}

	public void setInt(String key, int value) {
		mutate(state -> state.setInt(key, value));
	}

	public double getDouble(String key, double fallback) {
		return load().getDouble(key, fallback);
	}

	public void setDouble(String key, double value) {
		mutate(state -> state.setDouble(key, value));
	}

	public boolean getBoolean(String key, boolean fallback) {
		return load().getBoolean(key, fallback);
	}

	public void setBoolean(String key, boolean value) {
		mutate(state -> state.setBoolean(key, value));
	}

	public void remove(String key) {
		mutate(state -> state.remove(key));
	}

	public boolean contains(String key) {
		return load().contains(key);
	}

	public Map<String, String> snapshot() {
		return load().snapshot();
	}

	private DynamicEntityState load() {
		DynamicEntityState state = new DynamicEntityState();
		state.decode(stack.getOrDefault(DynamicRuntimeStateComponents.ITEM_STATE, ""));
		return state;
	}

	private void mutate(Consumer<DynamicEntityState> operation) {
		DynamicEntityState state = load();
		operation.accept(state);
		String encoded = state.encode();
		if (encoded.equals("{}")) stack.remove(DynamicRuntimeStateComponents.ITEM_STATE);
		else stack.set(DynamicRuntimeStateComponents.ITEM_STATE, encoded);
	}
}
