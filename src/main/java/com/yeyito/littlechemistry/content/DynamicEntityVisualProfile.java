package com.yeyito.littlechemistry.content;

import java.util.Locale;

/**
 * Fixed client model families that generated entities may reuse with a compatible authored skin.
 * The dimensions describe the vanilla carrier the baked model was designed around and let the
 * renderer fit that model to the generated entity's logical dimensions.
 */
public enum DynamicEntityVisualProfile {
	CUSTOM("custom", 1.0F, 1.0F, 0, 0),
	ZOMBIE("zombie", 0.6F, 1.95F, 64, 64),
	SKELETON("skeleton", 0.6F, 1.99F, 64, 32),
	ENDERMAN("enderman", 0.6F, 2.9F, 64, 32),
	COW("cow", 0.9F, 1.4F, 64, 64),
	PIG("pig", 0.9F, 0.9F, 64, 64),
	SPIDER("spider", 1.4F, 0.9F, 64, 32),
	CREEPER("creeper", 0.6F, 1.7F, 64, 32),
	BLAZE("blaze", 0.6F, 1.8F, 64, 32),
	COD("cod", 0.5F, 0.3F, 32, 32);

	private final String serializedName;
	private final float nativeWidth;
	private final float nativeHeight;
	private final int textureWidth;
	private final int textureHeight;

	DynamicEntityVisualProfile(String serializedName, float nativeWidth, float nativeHeight,
			int textureWidth, int textureHeight) {
		this.serializedName = serializedName;
		this.nativeWidth = nativeWidth;
		this.nativeHeight = nativeHeight;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
	}

	public String serializedName() {
		return serializedName;
	}

	public float nativeWidth() {
		return nativeWidth;
	}

	public float nativeHeight() {
		return nativeHeight;
	}

	public int textureWidth() {
		return textureWidth;
	}

	public int textureHeight() {
		return textureHeight;
	}

	public boolean usesVanillaModel() {
		return this != CUSTOM;
	}

	public static DynamicEntityVisualProfile parse(String raw) {
		if (raw == null) throw new IllegalArgumentException("Entity visual profile is required");
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		for (DynamicEntityVisualProfile profile : values()) {
			if (profile.serializedName.equals(normalized)) return profile;
		}
		throw new IllegalArgumentException("Unknown entity visual profile: " + raw);
	}
}
