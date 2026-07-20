package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Fill direction for a declarative workstation gauge. */
public enum DynamicWorkstationGaugeDirection {
	LEFT_TO_RIGHT("left_to_right"),
	RIGHT_TO_LEFT("right_to_left"),
	TOP_TO_BOTTOM("top_to_bottom"),
	BOTTOM_TO_TOP("bottom_to_top");

	private final String serializedName;

	DynamicWorkstationGaugeDirection(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicWorkstationGaugeDirection parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown workstation gauge direction: " + value, error);
		}
	}
}
