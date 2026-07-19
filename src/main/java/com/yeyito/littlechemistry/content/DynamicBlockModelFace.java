package com.yeyito.littlechemistry.content;

public record DynamicBlockModelFace(String texture, DynamicBlockUv uv) {
	public DynamicBlockModelFace {
		if (texture == null || !texture.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException("Block model face texture ID is invalid");
		}
	}
}
