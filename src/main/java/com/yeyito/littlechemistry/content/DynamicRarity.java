package com.yeyito.littlechemistry.content;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;

import java.util.Locale;

/** Little Chemistry rarity tiers, including tiers above vanilla's epic ceiling. */
public enum DynamicRarity {
	COMMON("common", ChatFormatting.WHITE, Rarity.COMMON),
	UNCOMMON("uncommon", ChatFormatting.YELLOW, Rarity.UNCOMMON),
	RARE("rare", ChatFormatting.AQUA, Rarity.RARE),
	EPIC("epic", ChatFormatting.LIGHT_PURPLE, Rarity.EPIC),
	LEGENDARY("legendary", ChatFormatting.GOLD, Rarity.EPIC),
	MYTHICAL("mythical", ChatFormatting.RED, Rarity.EPIC);

	private final String serializedName;
	private final ChatFormatting color;
	private final Rarity vanillaRarity;

	DynamicRarity(String serializedName, ChatFormatting color, Rarity vanillaRarity) {
		this.serializedName = serializedName;
		this.color = color;
		this.vanillaRarity = vanillaRarity;
	}

	public String serializedName() {
		return serializedName;
	}

	public ChatFormatting color() {
		return color;
	}

	/** Vanilla's data component has only four tiers, so higher tiers use EPIC mechanically. */
	public Rarity vanillaRarity() {
		return vanillaRarity;
	}

	public static DynamicRarity parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown dynamic rarity: " + value, error);
		}
	}

	public static DynamicRarity fromVanilla(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> COMMON;
			case UNCOMMON -> UNCOMMON;
			case RARE -> RARE;
			case EPIC -> EPIC;
		};
	}

	public static DynamicRarity fromProperties(DynamicBlockProperties block, DynamicItemProperties item,
			DynamicArmorProperties armor) {
		Rarity rarity = block != null ? block.rarity() : item != null ? item.rarity()
				: armor != null ? armor.rarity() : Rarity.COMMON;
		return fromVanilla(rarity);
	}
}
