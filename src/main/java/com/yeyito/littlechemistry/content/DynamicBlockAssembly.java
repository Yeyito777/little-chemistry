package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A bounded, face-connected multiblock footprint with arbitrary named geometry/collision variants. */
public record DynamicBlockAssembly(List<DynamicBlockAssemblyVariant> variants) {
	public static final int MAX_VARIANTS = 8;
	public static final int MAX_CELLS = 16;
	public static final int MAX_ELEMENTS_PER_VARIANT = 64;

	public DynamicBlockAssembly {
		variants = List.copyOf(variants);
		if (variants.isEmpty() || variants.size() > MAX_VARIANTS) {
			throw new IllegalArgumentException("Block assemblies require 1-" + MAX_VARIANTS + " variants");
		}
		Set<String> ids = new HashSet<>();
		Set<BlockPos> footprint = variants.getFirst().footprint();
		for (DynamicBlockAssemblyVariant variant : variants) {
			if (!ids.add(variant.id())) throw new IllegalArgumentException("Duplicate block assembly variant: " + variant.id());
			if (!variant.footprint().equals(footprint)) {
				throw new IllegalArgumentException("Every block assembly variant must reserve the same footprint");
			}
		}
		if (!faceConnected(footprint)) throw new IllegalArgumentException("Block assembly footprint must be face-connected");
	}

	public DynamicBlockAssemblyVariant initialVariant() {
		return variants.getFirst();
	}

	public DynamicBlockAssemblyVariant variant(int index) {
		return variants.get(Math.clamp(index, 0, variants.size() - 1));
	}

	public int variantIndex(String id) {
		for (int index = 0; index < variants.size(); index++) if (variants.get(index).id().equals(id)) return index;
		return -1;
	}

	public void validateTextures(DynamicBlockModel model) {
		if (model == null) throw new IllegalArgumentException("Block assemblies require a shared block texture model");
		DynamicBlockAssemblyCell root = initialVariant().cell(BlockPos.ZERO);
		if (root == null || !root.elements().equals(model.elements())) {
			throw new IllegalArgumentException("The initial assembly root geometry must equal blockModel.elements for item preview compatibility");
		}
		for (DynamicBlockAssemblyVariant variant : variants) for (DynamicBlockAssemblyCell cell : variant.cells()) {
			for (DynamicBlockModelElement element : cell.elements()) for (DynamicBlockModelFace face : element.faces().values()) {
				if (model.findTexture(face.texture()) == null) {
					throw new IllegalArgumentException("Block assembly face refers to unknown model texture: " + face.texture());
				}
			}
		}
	}

	private static boolean faceConnected(Set<BlockPos> footprint) {
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		pending.add(BlockPos.ZERO);
		while (!pending.isEmpty()) {
			BlockPos current = pending.removeFirst();
			if (!footprint.contains(current) || !visited.add(current)) continue;
			for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) pending.add(current.relative(direction));
		}
		return visited.size() == footprint.size();
	}
}
