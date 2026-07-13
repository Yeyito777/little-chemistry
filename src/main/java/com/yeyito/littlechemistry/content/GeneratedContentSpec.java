package com.yeyito.littlechemistry.content;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		DynamicArmorDisplayTextureSpec armorDisplayTexture,
		String behaviorSource
) {
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(texture, block, item, null, null, behaviorSource);
	}

	public GeneratedContentSpec {
		if (texture == null) {
			throw new IllegalArgumentException("Generated content requires a texture");
		}
		int propertyKinds = (block == null ? 0 : 1) + (item == null ? 0 : 1) + (armor == null ? 0 : 1);
		if (propertyKinds != 1) {
			throw new IllegalArgumentException("Generated content must contain exactly one property kind");
		}
		if (block != null) {
			texture.requireOpaque();
		} else {
			texture.requireBinaryAlpha();
		}
		if ((armor == null) != (armorDisplayTexture == null)) {
			throw new IllegalArgumentException("Generated armor requires a separate 64x32 display texture");
		}
		if (behaviorSource != null) {
			behaviorSource = behaviorSource.strip();
			if (behaviorSource.isEmpty()) behaviorSource = null;
			if (behaviorSource != null && (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0)) {
				throw new IllegalArgumentException("Generated behavior source is invalid");
			}
		}
	}

	public boolean hasBehavior() {
		return behaviorSource != null;
	}
}
