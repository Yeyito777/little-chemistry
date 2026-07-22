package com.yeyito.littlechemistry.content;

import java.util.List;
import java.util.Set;

/** A generated cuboid model or a compatible skin on one of the fixed animated vanilla profiles. */
public record DynamicEntityModel(
		DynamicEntityVisualProfile profile,
		DynamicBlockModel geometry,
		DynamicBlockTexture vanillaTexture
) {
	public DynamicEntityModel {
		if (profile == null) throw new IllegalArgumentException("Entity visual profile is required");
		if (profile == DynamicEntityVisualProfile.CUSTOM) {
			if (geometry == null || vanillaTexture != null) {
				throw new IllegalArgumentException("A custom entity model requires cuboid geometry only");
			}
			geometry.validateForEntity();
		} else {
			if (geometry != null || vanillaTexture == null) {
				throw new IllegalArgumentException("A vanilla entity model profile requires one compatible skin only");
			}
			vanillaTexture.texture().requireDimensions(profile.textureWidth(), profile.textureHeight());
		}
	}

	/** Compatibility constructor for the original generated-cuboid entity format. */
	public DynamicEntityModel(DynamicBlockModel geometry) {
		this(DynamicEntityVisualProfile.CUSTOM, geometry, null);
	}

	public static DynamicEntityModel vanilla(DynamicEntityVisualProfile profile, DynamicBlockTexture texture) {
		if (profile == DynamicEntityVisualProfile.CUSTOM) {
			throw new IllegalArgumentException("The custom profile requires cuboid geometry");
		}
		return new DynamicEntityModel(profile, null, texture);
	}

	public boolean usesVanillaModel() {
		return profile.usesVanillaModel();
	}

	public List<DynamicBlockTexture> textures() {
		return geometry == null ? List.of(vanillaTexture) : geometry.textures();
	}

	public List<DynamicBlockModelElement> elements() {
		return geometry == null ? List.of() : geometry.elements();
	}

	public String particleTexture() {
		return geometry == null ? vanillaTexture.id() : geometry.particleTexture();
	}

	public DynamicBlockTexture primaryTexture() {
		return geometry == null ? vanillaTexture : geometry.particleTextureAsset();
	}

	public Set<String> textureHashes() {
		return geometry == null ? Set.of(vanillaTexture.hash()) : geometry.textureHashes();
	}
}
