package com.yeyito.littlechemistry.content;

/** Selects whether Minecraft or generated Java owns a bow/crossbow's use-and-release lifecycle. */
public enum DynamicProjectileMechanics {
	NATIVE("native"),
	CUSTOM("custom");

	private final String serializedName;

	DynamicProjectileMechanics(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicProjectileMechanics parse(String value) {
		for (DynamicProjectileMechanics mechanics : values()) {
			if (mechanics.serializedName.equals(value)) return mechanics;
		}
		throw new IllegalArgumentException("Unknown projectile mechanics route: " + value);
	}
}
