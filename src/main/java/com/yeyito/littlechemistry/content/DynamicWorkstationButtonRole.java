package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Engine-owned and generated-code-owned workstation button roles. */
public enum DynamicWorkstationButtonRole {
	MAKE_RECIPE("make_recipe"),
	CUSTOM("custom");

	private final String serializedName;

	DynamicWorkstationButtonRole(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicWorkstationButtonRole parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown workstation button role: " + value, error);
		}
	}
}
