package com.yeyito.littlechemistry.content;

public enum DynamicItemType {
	ITEM("item"),
	FOOD("food"),
	TOOL("tool");

	private final String serializedName;

	DynamicItemType(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicItemType parse(String value) {
		for (DynamicItemType type : values()) {
			if (type.serializedName.equals(value)) return type;
		}
		throw new IllegalArgumentException("Unknown item type: " + value);
	}
}
