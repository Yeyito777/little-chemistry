package com.yeyito.littlechemistry.content;

/** Reusable native targeting profile for generated mobs. */
public enum DynamicEntityDisposition {
	PASSIVE("passive"),
	NEUTRAL("neutral"),
	HOSTILE("hostile");

	private final String serializedName;

	DynamicEntityDisposition(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicEntityDisposition parse(String value) {
		for (DynamicEntityDisposition disposition : values()) {
			if (disposition.serializedName.equals(value)) return disposition;
		}
		throw new IllegalArgumentException("Unknown dynamic entity disposition: " + value);
	}
}
