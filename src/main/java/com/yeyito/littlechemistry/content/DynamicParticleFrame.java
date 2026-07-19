package com.yeyito.littlechemistry.content;

public record DynamicParticleFrame(
		String textureHash,
		DynamicTextureSpec texture
) {
	public DynamicParticleFrame {
		if (textureHash == null || !textureHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Dynamic particle frame texture hash is invalid");
		}
		if (texture == null) {
			throw new IllegalArgumentException("Dynamic particle frame texture is required");
		}
	}
}
