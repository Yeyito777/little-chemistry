package com.yeyito.littlechemistry.content;

public record DynamicBlockTexture(String id, String hash, DynamicTextureSpec texture) {
	public DynamicBlockTexture {
		if (id == null || !id.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException("Block model texture ID is invalid");
		}
		if (hash == null || !hash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Block model texture hash is invalid");
		}
		if (texture == null) throw new IllegalArgumentException("Block model texture specification is required");
	}
}
