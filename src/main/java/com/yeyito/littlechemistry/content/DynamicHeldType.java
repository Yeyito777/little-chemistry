package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

public enum DynamicHeldType {
	REGULAR("regular"),
	TOOL("tool"),
	ROD("rod"),
	BOW("bow"),
	CROSSBOW("crossbow"),
	MACE("mace"),
	SPEAR("spear"),
	SHIELD("shield"),
	TRIDENT("trident");

	private final String serializedName;

	DynamicHeldType(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public ItemUseAnimation useAnimation() {
		return switch (this) {
			case BOW -> ItemUseAnimation.BOW;
			case CROSSBOW -> ItemUseAnimation.CROSSBOW;
			case SPEAR -> ItemUseAnimation.SPEAR;
			case SHIELD -> ItemUseAnimation.BLOCK;
			case TRIDENT -> ItemUseAnimation.TRIDENT;
			default -> ItemUseAnimation.NONE;
		};
	}

	public int useDuration(ItemStack stack, LivingEntity user) {
		return switch (this) {
			case BOW, CROSSBOW, SPEAR, SHIELD, TRIDENT -> 72_000;
			default -> 0;
		};
	}

	public int chargeDuration(ItemStack stack, LivingEntity user) {
		return switch (this) {
			case BOW -> 20;
			case CROSSBOW -> CrossbowItem.getChargeDuration(stack, user);
			case SPEAR, TRIDENT -> 10;
			default -> 1;
		};
	}

	/** Native archetypes whose vanilla mechanics consume durability and therefore must not silently become immortal. */
	public boolean requiresDurability() {
		return switch (this) {
			case ROD, BOW, CROSSBOW, MACE, SPEAR, SHIELD, TRIDENT -> true;
			default -> false;
		};
	}

	/** Native archetypes with model predicates that require a distinct authored runtime texture. */
	public boolean requiresVisualStates() {
		return switch (this) {
			case ROD, BOW, CROSSBOW, SHIELD, TRIDENT -> true;
			default -> false;
		};
	}

	public static DynamicHeldType parse(String value) {
		for (DynamicHeldType type : values()) {
			if (type.serializedName.equals(value)) return type;
		}
		throw new IllegalArgumentException("Unknown item held type: " + value);
	}

	/** Preserves the model transform used by catalogs written before heldType was stored independently. */
	public static DynamicHeldType legacyDefaultFor(DynamicItemType itemType) {
		return itemType == DynamicItemType.TOOL ? TOOL : REGULAR;
	}
}
