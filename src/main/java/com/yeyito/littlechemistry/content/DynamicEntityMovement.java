package com.yeyito.littlechemistry.content;

/** Native movement family used by a generated entity's registered carrier type. */
public enum DynamicEntityMovement {
	GROUND("ground"),
	FLYING("flying"),
	AQUATIC("aquatic"),
	AMPHIBIOUS("amphibious"),
	VEHICLE("vehicle");

	private final String serializedName;

	DynamicEntityMovement(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicEntityMovement parse(String value) {
		for (DynamicEntityMovement movement : values()) {
			if (movement.serializedName.equals(value)) return movement;
		}
		throw new IllegalArgumentException("Unknown dynamic entity movement: " + value);
	}
}
