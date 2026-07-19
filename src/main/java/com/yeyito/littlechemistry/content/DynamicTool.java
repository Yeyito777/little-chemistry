package com.yeyito.littlechemistry.content;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public enum DynamicTool {
	NONE("none"),
	PICKAXE("pickaxe"),
	AXE("axe"),
	SHOVEL("shovel"),
	HOE("hoe"),
	SWORD("sword");

	private final String serializedName;

	DynamicTool(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public TagKey<Block> mineableBlocks() {
		return switch (this) {
			case NONE -> null;
			case PICKAXE -> BlockTags.MINEABLE_WITH_PICKAXE;
			case AXE -> BlockTags.MINEABLE_WITH_AXE;
			case SHOVEL -> BlockTags.MINEABLE_WITH_SHOVEL;
			case HOE -> BlockTags.MINEABLE_WITH_HOE;
			case SWORD -> BlockTags.SWORD_EFFICIENT;
		};
	}

	public boolean matches(ItemStack stack) {
		if (this == NONE) return true;
		DynamicContentDefinition generated = DynamicContentObjects.CONTENT_ID == null
				? null : DynamicContentObjects.definition(stack);
		if (generated != null && generated.item() != null && generated.item().tool() != NONE) {
			return generated.item().tool() == this;
		}
		return switch (this) {
			case NONE -> true;
			case PICKAXE -> stack.is(ItemTags.PICKAXES);
			case AXE -> stack.is(ItemTags.AXES);
			case SHOVEL -> stack.is(ItemTags.SHOVELS);
			case HOE -> stack.is(ItemTags.HOES);
			case SWORD -> stack.is(ItemTags.SWORDS);
		};
	}

	public static DynamicTool parse(String value) {
		for (DynamicTool tool : values()) {
			if (tool.serializedName.equals(value)) {
				return tool;
			}
		}
		throw new IllegalArgumentException("Unknown preferred tool: " + value);
	}
}
