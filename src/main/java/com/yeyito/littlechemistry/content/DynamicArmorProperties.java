package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.Rarity;

public record DynamicArmorProperties(
		DynamicArmorSlot slot,
		Rarity rarity,
		boolean foil,
		int enchantability,
		double defense,
		double toughness,
		double knockbackResistance,
		int durability
) {
	public DynamicArmorProperties {
		if (slot == null) throw new IllegalArgumentException("Armor slot is required");
		if (rarity == null) throw new IllegalArgumentException("Armor rarity is required");
		if (enchantability < 0 || enchantability > 255) {
			throw new IllegalArgumentException("Armor enchantability must be between 0 and 255");
		}
		if (!Double.isFinite(defense) || defense < 0.0 || defense > 100.0) {
			throw new IllegalArgumentException("Armor defense must be between 0 and 100");
		}
		if (!Double.isFinite(toughness) || toughness < 0.0 || toughness > 100.0) {
			throw new IllegalArgumentException("Armor toughness must be between 0 and 100");
		}
		if (!Double.isFinite(knockbackResistance) || knockbackResistance < 0.0 || knockbackResistance > 1.0) {
			throw new IllegalArgumentException("Armor knockback resistance must be between 0 and 1");
		}
		if (durability < 1 || durability > 100_000) {
			throw new IllegalArgumentException("Armor durability must be between 1 and 100000");
		}
	}

	public static DynamicArmorProperties defaults(DynamicArmorSlot slot) {
		double defense = switch (slot) {
			case HEAD, BOOTS -> 2.0;
			case CHEST -> 6.0;
			case LEGGINGS -> 5.0;
		};
		int durability = switch (slot) {
			case HEAD -> 165;
			case CHEST -> 240;
			case LEGGINGS -> 225;
			case BOOTS -> 195;
		};
		return new DynamicArmorProperties(slot, Rarity.COMMON, false, 9, defense, 0.0, 0.0, durability);
	}
}
