package com.yeyito.littlechemistry.content;

public record DynamicContentDefinition(
		DynamicContentType type,
		String name,
		String displayName,
		long textureSeed,
		String textureHash
) {
	public DynamicContentDefinition {
		if (name == null || !name.matches("[a-z0-9_]{1,64}")) {
			throw new IllegalArgumentException("Dynamic content ID is invalid");
		}
		if (displayName == null || displayName.isBlank() || displayName.length() > 64
				|| displayName.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Dynamic content display name is invalid");
		}
		if (textureHash == null || !textureHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Dynamic content texture hash is invalid");
		}
	}

	public String idPath() {
		return name;
	}
}
