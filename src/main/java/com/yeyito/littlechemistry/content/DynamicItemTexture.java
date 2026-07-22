package com.yeyito.littlechemistry.content;

/** One named, content-addressed 16x16 visual state for a generated item. */
public record DynamicItemTexture(String id, String hash, DynamicTextureSpec texture) {
	public DynamicItemTexture {
		if (id == null || !id.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException("Item visual state ID is invalid");
		}
		if (hash == null || !hash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Item visual state texture hash is invalid");
		}
		if (texture == null) throw new IllegalArgumentException("Item visual state texture is required");
		texture.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
	}
}
