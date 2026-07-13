package com.yeyito.littlechemistry.content;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		String behaviorSource
) {
	public GeneratedContentSpec {
		if (texture == null) {
			throw new IllegalArgumentException("Generated content requires a texture");
		}
		if ((block == null) == (item == null)) {
			throw new IllegalArgumentException("Generated content must contain exactly one property kind");
		}
		if (block != null) {
			texture.requireOpaque();
		}
		if (behaviorSource == null || behaviorSource.isBlank()) {
			throw new IllegalArgumentException("Generated content requires compiled Java behavior source");
		}
	}
}
