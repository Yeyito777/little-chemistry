package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * A level access that marks a crafting menu as portable while retaining the
 * normal level context needed for recipe updates and crafted-item callbacks.
 */
public final class PortableCraftingAccess implements ContainerLevelAccess {
	private final Level level;
	private final BlockPos pos;
	private final UUID tableId;
	private final ItemStack carrier;

	public PortableCraftingAccess(Level level, BlockPos pos, UUID tableId, ItemStack carrier) {
		this.level = Objects.requireNonNull(level, "level");
		this.pos = Objects.requireNonNull(pos, "pos").immutable();
		this.tableId = Objects.requireNonNull(tableId, "tableId");
		this.carrier = Objects.requireNonNull(carrier, "carrier");
	}

	public Level level() {
		return level;
	}

	public UUID tableId() {
		return tableId;
	}

	public ItemStack carrier() {
		return carrier;
	}

	@Override
	public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> operation) {
		return Optional.of(operation.apply(level, pos));
	}
}
