package com.yeyito.littlechemistry.content;

public enum DynamicBlockShape {
	FULL_CUBE("full_cube"),
	SLAB("slab"),
	NO_COLLISION("no_collision"),
	STAR("star"),
	FENCE("fence");

	private final String serializedName;

	DynamicBlockShape(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicBlockShape parse(String value) {
		for (DynamicBlockShape shape : values()) if (shape.serializedName.equals(value)) return shape;
		throw new IllegalArgumentException("Unknown block shape: " + value);
	}
}
