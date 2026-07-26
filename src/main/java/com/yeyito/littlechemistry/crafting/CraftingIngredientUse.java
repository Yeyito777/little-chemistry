package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicCraftingUse;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Per-recipe treatment of one ordinary crafting-grid ingredient. */
public enum CraftingIngredientUse {
	/** Preserve Minecraft's native crafting remainder, if any. */
	DEFAULT("default"),
	CONSUME("consume"),
	KEEP("keep"),
	DAMAGE("damage");

	private final String serializedName;

	CraftingIngredientUse(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static CraftingIngredientUse parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("Unknown crafting ingredient use: " + value, invalid);
		}
	}

	/**
	 * Supplies the normal behavior when generation does not intentionally override a slot.
	 * Damageable native items act as reusable implements; generated items retain their authored contract.
	 */
	public static CraftingIngredientUse defaultFor(ItemStack stack) {
		if (stack.isEmpty()) return DEFAULT;
		DynamicContentDefinition definition = DynamicContentObjects.CONTENT_ID == null
				? null : DynamicContentObjects.definition(stack);
		if (definition != null && definition.item() != null) {
			DynamicCraftingUse use = definition.item().craftingUse();
			return switch (use) {
				case CONSUME -> CONSUME;
				case KEEP -> KEEP;
				case DAMAGE -> DAMAGE;
			};
		}
		if (stack.getItem().getCraftingRemainder() != null) return DEFAULT;
		return CONSUME;
	}

	static List<CraftingIngredientUse> resolvedDefaults(List<ItemStack> ingredients) {
		List<CraftingIngredientUse> uses = new ArrayList<>(ingredients.size());
		for (ItemStack ingredient : ingredients) uses.add(defaultFor(ingredient));
		return List.copyOf(uses);
	}
}
