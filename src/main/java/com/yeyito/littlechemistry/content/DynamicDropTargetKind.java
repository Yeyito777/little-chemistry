package com.yeyito.littlechemistry.content;

import java.util.Locale;

/** Distinguishes registry items from Little Chemistry's virtual content IDs. */
public enum DynamicDropTargetKind {
	SELF("self"),
	REGISTERED_ITEM("registered_item"),
	DYNAMIC_CONTENT("dynamic_content");

	private final String serializedName;

	DynamicDropTargetKind(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static DynamicDropTargetKind parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("Unknown drop target kind: " + value, error);
		}
	}
}
