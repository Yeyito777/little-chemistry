package com.yeyito.littlechemistry.content;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item
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
	}
}
