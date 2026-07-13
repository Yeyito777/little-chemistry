package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record DynamicPlacementProperties(
		DynamicPlacedShape shape,
		List<String> supports,
		int lightLevel,
		boolean visuallyEmissive
) {
	public DynamicPlacementProperties {
		if (shape == null) throw new IllegalArgumentException("Placed item shape is required");
		if (supports == null || supports.isEmpty() || supports.size() > 16) {
			throw new IllegalArgumentException("Placeable items require between 1 and 16 support rules");
		}
		supports = List.copyOf(supports);
		for (String support : supports) validateSupport(support);
		if (lightLevel < 0 || lightLevel > 15) {
			throw new IllegalArgumentException("Placed item light level must be between 0 and 15");
		}
	}

	public boolean canPlaceOn(BlockState state, BlockGetter level, BlockPos position) {
		for (String support : supports) {
			if (support.equals("any_solid")) {
				if (state.isFaceSturdy(level, position, Direction.UP)) return true;
				continue;
			}
			boolean tag = support.startsWith("#");
			Identifier id = Identifier.parse(tag ? support.substring(1) : support);
			if (tag) {
				if (state.is(TagKey.create(Registries.BLOCK, id))) return true;
			} else {
				Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
				if (block != null && state.is(block)) return true;
			}
		}
		return false;
	}

	private static void validateSupport(String support) {
		if (support == null || support.isBlank() || support.length() > 128) {
			throw new IllegalArgumentException("Invalid placement support rule");
		}
		if (support.equals("any_solid")) return;
		boolean tag = support.startsWith("#");
		String value = tag ? support.substring(1) : support;
		Identifier id = Identifier.tryParse(value);
		if (id == null) throw new IllegalArgumentException("Invalid placement support identifier: " + support);
		if (tag) {
			if (BuiltInRegistries.BLOCK.get(TagKey.create(Registries.BLOCK, id)).isEmpty()) {
				throw new IllegalArgumentException("Unknown or empty placement support tag: " + support);
			}
		} else if (!BuiltInRegistries.BLOCK.containsKey(id)) {
			throw new IllegalArgumentException("Unknown placement support block: " + support);
		}
	}
}
