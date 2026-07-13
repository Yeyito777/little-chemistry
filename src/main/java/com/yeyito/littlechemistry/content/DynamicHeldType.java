package com.yeyito.littlechemistry.content;

public enum DynamicHeldType {
	REGULAR("regular"),
	TOOL("tool");

	private final String serializedName;

	DynamicHeldType(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
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
