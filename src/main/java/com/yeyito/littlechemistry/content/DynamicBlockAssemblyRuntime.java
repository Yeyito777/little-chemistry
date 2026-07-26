package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Coordinate, root-resolution, and state-selection operations shared by all generated block assemblies. */
public final class DynamicBlockAssemblyRuntime {
	private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
	private DynamicBlockAssemblyRuntime() {
	}

	/** Rotates a north-authored cell offset into world space. */
	public static BlockPos rotateOffset(BlockPos local, Direction facing) {
		return switch (facing) {
			case EAST -> new BlockPos(-local.getZ(), local.getY(), local.getX());
			case SOUTH -> new BlockPos(-local.getX(), local.getY(), -local.getZ());
			case WEST -> new BlockPos(local.getZ(), local.getY(), -local.getX());
			default -> local;
		};
	}

	public static BlockPos worldPosition(BlockPos root, BlockPos local, Direction facing) {
		return root.offset(rotateOffset(local, facing));
	}

	public static BlockPos rootPosition(BlockPos member, BlockPos local, Direction facing) {
		BlockPos rotated = rotateOffset(local, facing);
		return member.offset(-rotated.getX(), -rotated.getY(), -rotated.getZ());
	}

	public static @Nullable DynamicBlockEntity rootEntity(BlockGetter level, BlockPos member) {
		if (!(level.getBlockEntity(member) instanceof DynamicBlockEntity entity)) return null;
		BlockPos offset = entity.assemblyOffset();
		if (offset.equals(BlockPos.ZERO)) return entity;
		Direction facing = entity.getBlockState().getValue(DynamicCarrierBlock.FACING);
		BlockPos rootPosition = rootPosition(member, offset, facing);
		if (!(level.getBlockEntity(rootPosition) instanceof DynamicBlockEntity root)
				|| !root.assemblyOffset().equals(BlockPos.ZERO)
				|| !java.util.Objects.equals(root.contentId(), entity.contentId())) return null;
		return root;
	}

	public static List<DynamicBlockModelElement> selectedElements(DynamicContentDefinition definition,
			BlockPos localOffset, BlockState state) {
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		if (assembly == null) return definition == null || definition.blockModel() == null
				? List.of() : definition.blockModel().elements();
		int index = state.getValue(DynamicCarrierBlock.ASSEMBLY_VARIANT);
		DynamicBlockAssemblyCell cell = assembly.variant(index).cell(localOffset);
		return cell == null ? List.of() : cell.elements();
	}

	public static String selectedVariantId(DynamicContentDefinition definition, BlockState state) {
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		return assembly == null ? "" : assembly.variant(state.getValue(DynamicCarrierBlock.ASSEMBLY_VARIANT)).id();
	}

	static boolean beginRemoval() {
		if (REMOVING.get()) return false;
		REMOVING.set(true);
		return true;
	}

	static void endRemoval() { REMOVING.set(false); }

	static boolean removing() { return REMOVING.get(); }

	/** Removes every verified member except the cell already being removed by Minecraft. */
	static void removeOtherMembers(Level level, DynamicBlockEntity root, BlockPos keptPosition) {
		DynamicContentDefinition definition = root.contentId() == null ? null : DynamicContentCatalog.find(root.contentId());
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		if (assembly == null) return;
		Direction facing = root.getBlockState().getValue(DynamicCarrierBlock.FACING);
		for (BlockPos local : assembly.initialVariant().footprint()) {
			BlockPos memberPosition = worldPosition(root.getBlockPos(), local, facing);
			if (memberPosition.equals(keptPosition)) continue;
			if (level.getBlockState(memberPosition).is(DynamicContentObjects.BLOCK)
					&& level.getBlockEntity(memberPosition) instanceof DynamicBlockEntity member
					&& member.assemblyOffset().equals(local)
					&& java.util.Objects.equals(member.contentId(), root.contentId())) {
				level.removeBlock(memberPosition, false);
			}
		}
	}

	static void synchronizeVisualState(Level level, DynamicBlockEntity root, @Nullable String value) {
		DynamicContentDefinition definition = root.contentId() == null ? null : DynamicContentCatalog.find(root.contentId());
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		if (assembly == null) return;
		Direction facing = root.getBlockState().getValue(DynamicCarrierBlock.FACING);
		for (BlockPos local : assembly.initialVariant().footprint()) {
			BlockPos position = worldPosition(root.getBlockPos(), local, facing);
			if (level.getBlockEntity(position) instanceof DynamicBlockEntity member
					&& member.assemblyOffset().equals(local)
					&& java.util.Objects.equals(member.contentId(), root.contentId())) {
				member.setMirroredVisualState(value);
				level.sendBlockUpdated(position, member.getBlockState(), member.getBlockState(), 3);
			}
		}
	}

	static void synchronizeTransientVisualState(Level level, DynamicBlockEntity root, @Nullable String value) {
		DynamicContentDefinition definition = root.contentId() == null ? null : DynamicContentCatalog.find(root.contentId());
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		if (assembly == null) return;
		Direction facing = root.getBlockState().getValue(DynamicCarrierBlock.FACING);
		for (BlockPos local : assembly.initialVariant().footprint()) {
			BlockPos position = worldPosition(root.getBlockPos(), local, facing);
			if (level.getBlockEntity(position) instanceof DynamicBlockEntity member
					&& member.assemblyOffset().equals(local)
					&& java.util.Objects.equals(member.contentId(), root.contentId())) {
				member.setTransientVisualState(value);
				level.sendBlockUpdated(position, member.getBlockState(), member.getBlockState(), 3);
			}
		}
	}

	/** Synchronizes the named visual/geometry state across all reserved cells. Unknown names use the initial state. */
	public static void setVariant(Level level, DynamicBlockEntity root, @Nullable String variantId) {
		DynamicContentDefinition definition = root.contentId() == null ? null : DynamicContentCatalog.find(root.contentId());
		DynamicBlockAssembly assembly = definition == null || definition.block() == null
				? null : definition.block().assembly();
		if (assembly == null || !root.assemblyOffset().equals(BlockPos.ZERO)) return;
		int index = variantId == null ? 0 : assembly.variantIndex(variantId);
		if (index < 0) index = 0;
		Direction facing = root.getBlockState().getValue(DynamicCarrierBlock.FACING);
		for (BlockPos local : assembly.initialVariant().footprint()) {
			BlockPos position = worldPosition(root.getBlockPos(), local, facing);
			BlockState state = level.getBlockState(position);
			if (!state.is(DynamicContentObjects.BLOCK)
					|| !(level.getBlockEntity(position) instanceof DynamicBlockEntity member)
					|| !member.assemblyOffset().equals(local)
					|| !java.util.Objects.equals(member.contentId(), root.contentId())
					|| state.getValue(DynamicCarrierBlock.ASSEMBLY_VARIANT) == index) continue;
			level.setBlock(position, state.setValue(DynamicCarrierBlock.ASSEMBLY_VARIANT, index), 3);
		}
	}
}
