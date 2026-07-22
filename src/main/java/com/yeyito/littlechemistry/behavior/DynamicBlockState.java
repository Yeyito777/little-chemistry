package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Objects;

/** Persistent and client-synchronized bounded state for one placed generated block. */
public final class DynamicBlockState {
	private final ServerLevel level;
	private final BlockPos position;

	private DynamicBlockState(ServerLevel level, BlockPos position) {
		this.level = Objects.requireNonNull(level, "level");
		this.position = Objects.requireNonNull(position, "position").immutable();
	}

	public static DynamicBlockState at(ServerLevel level, BlockPos position) {
		return new DynamicBlockState(level, position);
	}

	public String getString(String key, String fallback) {
		String value = blockEntity().generatedState(key);
		return value == null ? fallback : value;
	}

	public void setString(String key, String value) {
		blockEntity().setGeneratedState(key, value);
	}

	public int getInt(String key, int fallback) {
		try {
			return Integer.parseInt(getString(key, Integer.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public void setInt(String key, int value) {
		setString(key, Integer.toString(value));
	}

	public double getDouble(String key, double fallback) {
		try {
			double value = Double.parseDouble(getString(key, Double.toString(fallback)));
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public void setDouble(String key, double value) {
		if (!Double.isFinite(value)) throw new IllegalArgumentException("Block state numbers must be finite");
		setString(key, Double.toString(value));
	}

	public boolean getBoolean(String key, boolean fallback) {
		String value = getString(key, Boolean.toString(fallback));
		return value.equalsIgnoreCase("true") ? true : value.equalsIgnoreCase("false") ? false : fallback;
	}

	public void setBoolean(String key, boolean value) {
		setString(key, Boolean.toString(value));
	}

	public void remove(String key) {
		setString(key, null);
	}

	public Map<String, String> snapshot() {
		return blockEntity().generatedStateSnapshot();
	}

	private DynamicBlockEntity blockEntity() {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity dynamic) return dynamic;
		throw new IllegalStateException("No generated block exists at " + position.toShortString());
	}
}
