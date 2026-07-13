package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.Rarity;

public record DynamicItemProperties(
		DynamicItemType itemType,
		DynamicHeldType heldType,
		int maxStack,
		Rarity rarity,
		boolean foil,
		int enchantability,
		double reach,
		DynamicTool tool,
		DynamicBreakingPower breakingPower,
		float breakingSpeed,
		double attackDamage,
		double attackSpeed,
		int durability,
		int damagePerBlock,
		int damagePerAttack,
		DynamicFoodProperties food,
		DynamicPlacementProperties placement
) {
	public static final DynamicItemProperties DEFAULT = ordinary(64, Rarity.COMMON, false, 0, 0.0);

	public DynamicItemProperties {
		if (itemType == null || heldType == null || rarity == null || tool == null || breakingPower == null) {
			throw new IllegalArgumentException("Item type, held type, rarity, tool, and breaking power are required");
		}
		if (maxStack < 1 || maxStack > 64) throw new IllegalArgumentException("Item stack size must be between 1 and 64");
		if (enchantability < 0 || enchantability > 255) throw new IllegalArgumentException("Enchantability must be between 0 and 255");
		if (!Double.isFinite(reach) || reach < 0.0 || reach > 16.0) throw new IllegalArgumentException("Item reach bonus must be between 0 and 16 blocks");
		if (!Float.isFinite(breakingSpeed) || breakingSpeed < 0.1F || breakingSpeed > 64.0F) throw new IllegalArgumentException("Item breaking speed must be between 0.1 and 64");
		if (!Double.isFinite(attackDamage) || attackDamage < 0.0 || attackDamage > 100.0) throw new IllegalArgumentException("Attack damage must be between 0 and 100");
		if (!Double.isFinite(attackSpeed) || attackSpeed < 0.1 || attackSpeed > 20.0) throw new IllegalArgumentException("Attack speed must be between 0.1 and 20");
		if (durability < 0 || durability > 100_000) throw new IllegalArgumentException("Durability must be between 0 and 100000");
		if (damagePerBlock < 0 || damagePerBlock > 64 || damagePerAttack < 0 || damagePerAttack > 64) {
			throw new IllegalArgumentException("Durability costs must be between 0 and 64");
		}
		if (itemType != DynamicItemType.TOOL) {
			if (tool != DynamicTool.NONE || breakingPower != DynamicBreakingPower.NONE || durability != 0
					|| attackDamage != 0.0 || attackSpeed != 4.0 || damagePerBlock != 0 || damagePerAttack != 0) {
				throw new IllegalArgumentException("Items and food cannot carry tool-only properties");
			}
			if (itemType == DynamicItemType.ITEM && food != null) throw new IllegalArgumentException("Ordinary items cannot carry food properties");
			if (itemType == DynamicItemType.FOOD && food == null) throw new IllegalArgumentException("Food items require food properties");
			if (itemType == DynamicItemType.FOOD && placement != null) throw new IllegalArgumentException("Food items cannot be placeable");
		} else {
			if (food != null || placement != null) throw new IllegalArgumentException("Tools cannot be food or placeable");
			if (tool == DynamicTool.NONE || breakingPower == DynamicBreakingPower.NONE) {
				throw new IllegalArgumentException("Tools require a tool category and breaking power");
			}
			if (maxStack != 1 || durability < 1) {
				throw new IllegalArgumentException("Tools must have maxStack 1 and positive durability");
			}
		}
	}

	public static DynamicItemProperties ordinary(int maxStack, Rarity rarity, boolean foil, int enchantability, double reach) {
		return new DynamicItemProperties(DynamicItemType.ITEM, DynamicHeldType.REGULAR,
				maxStack, rarity, foil, enchantability, reach,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0, 0, 0, 0, null, null);
	}
}
