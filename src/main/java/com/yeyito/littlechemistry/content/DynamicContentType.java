package com.yeyito.littlechemistry.content;

public enum DynamicContentType {
	ITEM("item"),
	BLOCK("block"),
	ARMOR("armor");

	private final String serializedName;

	DynamicContentType(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicContentType fromSerializedName(String value) {
		for (DynamicContentType type : values()) {
			if (type.serializedName.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown dynamic content type: " + value);
	}
}
