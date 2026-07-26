package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;

import java.util.List;

/** One reserved block cell and its cell-local 0..16 cuboids in a generated assembly variant. */
public record DynamicBlockAssemblyCell(int offsetX, int offsetY, int offsetZ,
		List<DynamicBlockModelElement> elements) {
	public DynamicBlockAssemblyCell {
		if (Math.abs(offsetX) > 4 || Math.abs(offsetY) > 4 || Math.abs(offsetZ) > 4) {
			throw new IllegalArgumentException("Block assembly offsets must stay between -4 and 4");
		}
		elements = List.copyOf(elements);
		if (elements.size() > DynamicBlockModel.MAX_ELEMENTS) {
			throw new IllegalArgumentException("One block assembly cell may have at most "
					+ DynamicBlockModel.MAX_ELEMENTS + " elements");
		}
	}

	public BlockPos offset() {
		return new BlockPos(offsetX, offsetY, offsetZ);
	}
}
