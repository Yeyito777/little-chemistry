package com.yeyito.littlechemistry.content;

public record DynamicContentDefinition(
		DynamicContentType type,
		String name,
		String displayName,
		long textureSeed,
		String textureHash,
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		String behaviorSource
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
		if (behaviorSource != null) {
			behaviorSource = behaviorSource.strip();
			if (behaviorSource.isEmpty()) behaviorSource = null;
			if (behaviorSource != null && (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0)) {
				throw new IllegalArgumentException("Dynamic behavior source is invalid");
			}
		}
		if (type == DynamicContentType.BLOCK) {
			if (block == null || item != null) {
				throw new IllegalArgumentException("Block content must have block properties only");
			}
			if (texture != null) {
				texture.requireOpaque();
			}
		} else {
			if (item == null || block != null) {
				throw new IllegalArgumentException("Item content must have item properties only");
			}
			if (texture != null) {
				texture.requireBinaryAlpha();
			}
		}
	}

	public String idPath() {
		return name;
	}

	public boolean hasBehavior() {
		return behaviorSource != null;
	}
}
