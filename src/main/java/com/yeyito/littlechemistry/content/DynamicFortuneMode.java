package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Bounded Fortune behavior available to declarative dynamic drops. */
public enum DynamicFortuneMode {
	NONE("none"),
	ORE_LIKE("ore_like");

	private final String serializedName;

	DynamicFortuneMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicFortuneMode parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown Fortune mode: " + value, error);
		}
	}
}
