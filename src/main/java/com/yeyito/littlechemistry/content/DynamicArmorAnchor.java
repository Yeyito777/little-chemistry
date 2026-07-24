package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Humanoid body part to which an authored armor cuboid is attached and animated. */
public enum DynamicArmorAnchor {
	HEAD,
	BODY,
	RIGHT_ARM,
	LEFT_ARM,
	RIGHT_LEG,
	LEFT_LEG;

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static DynamicArmorAnchor parse(String value) {
		for (DynamicArmorAnchor anchor : values()) {
			if (anchor.serializedName().equals(value)) return anchor;
		}
		throw new IllegalArgumentException("Unknown dynamic armor anchor: " + value);
	}

	public boolean isAvailableTo(DynamicArmorSlot slot) {
		return switch (slot) {
			case HEAD -> this == HEAD;
			case CHEST -> this == BODY || this == RIGHT_ARM || this == LEFT_ARM;
			case LEGGINGS -> this == BODY || this == RIGHT_LEG || this == LEFT_LEG;
			case BOOTS -> this == RIGHT_LEG || this == LEFT_LEG;
		};
	}

	public String modelPartName() {
		return serializedName();
	}
}
