package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Semantic inventory roles understood by the generic workstation runtime. */
public enum DynamicWorkstationSlotRole {
	INPUT("input"),
	CATALYST("catalyst"),
	FUEL("fuel"),
	OUTPUT("output"),
	BYPRODUCT("byproduct"),
	STORAGE("storage"),
	CUSTOM("custom");

	private final String serializedName;

	DynamicWorkstationSlotRole(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	/** Slots with these roles may be captured as ingredients for a newly generated recipe. */
	public boolean capturesRecipeInput() {
		return this == INPUT || this == CATALYST || this == FUEL;
	}

	public boolean isOutput() {
		return this == OUTPUT || this == BYPRODUCT;
	}

	public static DynamicWorkstationSlotRole parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown workstation slot role: " + value, error);
		}
	}
}
