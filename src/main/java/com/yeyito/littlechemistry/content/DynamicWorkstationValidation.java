package com.yeyito.littlechemistry.content;

import net.minecraft.resources.Identifier;

/** Shared validation for the declarative, client-visible workstation model. */
final class DynamicWorkstationValidation {
	private DynamicWorkstationValidation() {
	}

	static String id(String value, String description) {
		if (value == null || !value.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException(description + " ID is invalid");
		}
		return value;
	}

	static String text(String value, String description, int maximumLength, boolean allowLineBreaks) {
		if (value == null) throw new IllegalArgumentException(description + " is required");
		String normalized = allowLineBreaks
				? value.replace("\r\n", "\n").replace('\r', '\n').strip()
				: value.strip();
		if (normalized.isEmpty() || normalized.length() > maximumLength) {
			throw new IllegalArgumentException(description + " must contain 1-" + maximumLength + " characters");
		}
		if (normalized.chars().anyMatch(character -> Character.isISOControl(character)
				&& !(allowLineBreaks && character == '\n'))) {
			throw new IllegalArgumentException(description + " contains unsupported control characters");
		}
		return normalized;
	}

	static String optionalText(String value, String description, int maximumLength, boolean allowLineBreaks) {
		return value == null ? null : text(value, description, maximumLength, allowLineBreaks);
	}

	static String color(String value, String description) {
		if (value == null || !value.matches("[0-9A-Fa-f]{8}")) {
			throw new IllegalArgumentException(description + " must use RRGGBBAA hexadecimal notation");
		}
		return value.toUpperCase(java.util.Locale.ROOT);
	}

	static String optionalIdentifier(String value, String description) {
		if (value == null) return null;
		String normalized = value.strip();
		try {
			Identifier.parse(normalized);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(description + " must be a namespaced resource ID", error);
		}
		return normalized;
	}

	static void rectangle(String description, int x, int y, int width, int height,
			int screenWidth, int screenHeight) {
		if (x < 0 || y < 0 || width < 1 || height < 1
				|| x > screenWidth - width || y > screenHeight - height) {
			throw new IllegalArgumentException(description + " must fit inside the workstation screen");
		}
	}
}
