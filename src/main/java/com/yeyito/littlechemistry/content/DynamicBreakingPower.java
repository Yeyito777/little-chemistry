package com.yeyito.littlechemistry.content;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public enum DynamicBreakingPower {
	NONE("none", null),
	WOOD("wood", BlockTags.INCORRECT_FOR_WOODEN_TOOL),
	GOLD("gold", BlockTags.INCORRECT_FOR_GOLD_TOOL),
	STONE("stone", BlockTags.INCORRECT_FOR_STONE_TOOL),
	COPPER("copper", BlockTags.INCORRECT_FOR_COPPER_TOOL),
	IRON("iron", BlockTags.INCORRECT_FOR_IRON_TOOL),
	DIAMOND("diamond", BlockTags.INCORRECT_FOR_DIAMOND_TOOL),
	NETHERITE("netherite", BlockTags.INCORRECT_FOR_NETHERITE_TOOL);

	private final String serializedName;
	private final TagKey<Block> incorrectBlocks;

	DynamicBreakingPower(String serializedName, TagKey<Block> incorrectBlocks) {
		this.serializedName = serializedName;
		this.incorrectBlocks = incorrectBlocks;
	}

	public String serializedName() {
		return serializedName;
	}

	public TagKey<Block> incorrectBlocks() {
		return incorrectBlocks;
	}

	public static DynamicBreakingPower parse(String value) {
		for (DynamicBreakingPower power : values()) {
			if (power.serializedName.equals(value)) return power;
		}
		throw new IllegalArgumentException("Unknown breaking power: " + value);
	}
}
