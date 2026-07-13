package com.yeyito.littlechemistry.content;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		String behaviorSource
) {
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(texture, block, item, null, behaviorSource);
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
		if (behaviorSource == null || behaviorSource.isBlank()) {
			throw new IllegalArgumentException("Generated content requires compiled Java behavior source");
		}
	}
}
