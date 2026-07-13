package com.yeyito.littlechemistry.content;

import java.util.Locale;

public enum DynamicPlacedShape {
	CROSS,
	TORCH;

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static DynamicPlacedShape parse(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
