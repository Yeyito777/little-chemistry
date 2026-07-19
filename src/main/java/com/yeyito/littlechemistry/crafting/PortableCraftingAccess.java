package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A level access that marks a crafting menu as portable while retaining the
 * normal level context needed for recipe updates and crafted-item callbacks.
 */
public final class PortableCraftingAccess implements ContainerLevelAccess {
	private final Level level;
	private final BlockPos pos;

	public PortableCraftingAccess(Level level, BlockPos pos) {
		this.level = Objects.requireNonNull(level, "level");
		this.pos = Objects.requireNonNull(pos, "pos").immutable();
	}

	public Level level() {
		return level;
	}

	@Override
	public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> operation) {
		return Optional.of(operation.apply(level, pos));
	}
}
