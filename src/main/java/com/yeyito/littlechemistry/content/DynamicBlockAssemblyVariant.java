package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete geometry/collision state for every reserved cell of a generated block assembly. */
public record DynamicBlockAssemblyVariant(String id, List<DynamicBlockAssemblyCell> cells) {
	public DynamicBlockAssemblyVariant {
		if (id == null || !id.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException("Block assembly variant ID is invalid");
		}
		cells = List.copyOf(cells);
		if (cells.isEmpty() || cells.size() > DynamicBlockAssembly.MAX_CELLS) {
			throw new IllegalArgumentException("Block assembly variants require 1-" + DynamicBlockAssembly.MAX_CELLS + " cells");
		}
		Set<BlockPos> offsets = new HashSet<>();
		int elements = 0;
		for (DynamicBlockAssemblyCell cell : cells) {
			if (!offsets.add(cell.offset())) throw new IllegalArgumentException("Duplicate block assembly cell offset: " + cell.offset());
			elements += cell.elements().size();
		}
		if (!offsets.contains(BlockPos.ZERO)) throw new IllegalArgumentException("Block assemblies require a root cell at 0,0,0");
		if (elements < 1 || elements > DynamicBlockAssembly.MAX_ELEMENTS_PER_VARIANT) {
			throw new IllegalArgumentException("Block assembly variants require 1-"
					+ DynamicBlockAssembly.MAX_ELEMENTS_PER_VARIANT + " total elements");
		}
	}

	public DynamicBlockAssemblyCell cell(BlockPos offset) {
		for (DynamicBlockAssemblyCell cell : cells) if (cell.offset().equals(offset)) return cell;
		return null;
	}

	public Set<BlockPos> footprint() {
		return cells.stream().map(DynamicBlockAssemblyCell::offset).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}
}
