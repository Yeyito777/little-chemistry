package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** What happens to a generated item after it occupies an ingredient slot in an AI recipe. */
public enum DynamicCraftingUse {
	CONSUME("consume"),
	KEEP("keep"),
	DAMAGE("damage");

	private final String serializedName;

	DynamicCraftingUse(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicCraftingUse parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown crafting use: " + value, error);
		}
	}
}
