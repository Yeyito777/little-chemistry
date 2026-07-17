package com.yeyito.littlechemistry.content;

public record DynamicContentDefinition(
		DynamicContentType type,
		String name,
		String displayName,
		long textureSeed,
		String textureHash,
		DynamicTextureSpec texture,
		String armorDisplayTextureHash,
		DynamicArmorDisplayTextureSpec armorDisplayTexture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		String behaviorSource
) {
	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(type, name, displayName, textureSeed, textureHash, texture, null, null,
				block, item, null, behaviorSource);
	}

	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor, String behaviorSource) {
		this(type, name, displayName, textureSeed, textureHash, texture, null, null,
				block, item, armor, behaviorSource);
	}

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
		if ((armorDisplayTextureHash == null) != (armorDisplayTexture == null)) {
			throw new IllegalArgumentException("Armor display texture hash and specification must be supplied together");
		}
		if (armorDisplayTextureHash != null && !armorDisplayTextureHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Dynamic armor display texture hash is invalid");
		}
		if (behaviorSource != null) {
			behaviorSource = behaviorSource.strip();
			if (behaviorSource.isEmpty()) behaviorSource = null;
			if (behaviorSource != null && (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0)) {
				throw new IllegalArgumentException("Dynamic behavior source is invalid");
			}
		}
			switch (type) {
			case BLOCK -> {
				if (block == null || item != null || armor != null || armorDisplayTexture != null) {
					throw new IllegalArgumentException("Block content must have block properties only");
				}
				if (texture != null) {
					if (block.shape() == DynamicBlockShape.STAR) texture.requireBinaryAlpha();
					else texture.requireOpaque();
				}
			}
			case ITEM -> {
				if (item == null || block != null || armor != null || armorDisplayTexture != null) {
					throw new IllegalArgumentException("Item content must have item properties only");
				}
				if (texture != null) texture.requireBinaryAlpha();
			}
			case ARMOR -> {
				if (armor == null || block != null || item != null) {
					throw new IllegalArgumentException("Armor content must have armor properties only");
				}
				if (texture != null) texture.requireBinaryAlpha();
			}
		}
	}

	public String idPath() {
		return name;
	}

	public boolean hasBehavior() {
		return behaviorSource != null;
	}

	/**
	 * Legacy armor definitions did not store separate worn artwork and continue to use their icon hash
	 * until they are recreated. New definitions always return the dedicated 64x32 asset hash.
	 */
	public String effectiveArmorDisplayTextureHash() {
		if (type != DynamicContentType.ARMOR) {
			throw new IllegalStateException("Only armor has an equipped display texture");
		}
		return armorDisplayTextureHash == null ? textureHash : armorDisplayTextureHash;
	}
}
