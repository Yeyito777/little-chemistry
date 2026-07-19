package com.yeyito.littlechemistry.content;

/** Optional Minecraft-model UV rectangle, expressed in the conventional 0-16 model texture space. */
public record DynamicBlockUv(float u0, float v0, float u1, float v1) {
	public DynamicBlockUv {
		if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
				|| u0 < 0 || u0 > 16 || v0 < 0 || v0 > 16
				|| u1 < 0 || u1 > 16 || v1 < 0 || v1 > 16 || u0 == u1 || v0 == v1) {
			throw new IllegalArgumentException("Block face UV coordinates must be finite, distinct, and between 0 and 16");
		}
	}
}
