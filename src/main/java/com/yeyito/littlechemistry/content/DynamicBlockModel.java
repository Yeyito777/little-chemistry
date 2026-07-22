package com.yeyito.littlechemistry.content;

import net.minecraft.core.Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime visual model: named texture assets, preset-model face materials, and optional custom cuboids. */
public record DynamicBlockModel(
		List<DynamicBlockTexture> textures,
		String particleTexture,
		Map<Direction, DynamicBlockModelFace> faces,
		List<DynamicBlockModelElement> elements
) {
	public static final int MAX_TEXTURES = 12;
	public static final int MAX_ELEMENTS = 24;

	public DynamicBlockModel {
		textures = List.copyOf(textures);
		elements = List.copyOf(elements);
		if (textures.isEmpty() || textures.size() > MAX_TEXTURES) {
			throw new IllegalArgumentException("Block models require 1-12 textures");
		}
		if (elements.size() > MAX_ELEMENTS) throw new IllegalArgumentException("Custom block models may have at most 24 elements");
		Set<String> textureIds = new HashSet<>();
		for (DynamicBlockTexture texture : textures) {
			if (!textureIds.add(texture.id())) throw new IllegalArgumentException("Duplicate block model texture ID: " + texture.id());
		}
		if (!textureIds.contains(particleTexture)) {
			throw new IllegalArgumentException("Block particle texture must refer to a declared model texture");
		}
		EnumMap<Direction, DynamicBlockModelFace> copiedFaces = new EnumMap<>(Direction.class);
		copiedFaces.putAll(faces);
		for (Direction direction : Direction.values()) {
			DynamicBlockModelFace face = copiedFaces.get(direction);
			if (face == null) throw new IllegalArgumentException("Block model is missing its " + direction.getSerializedName() + " default face");
			validateTextureReference(face, textureIds);
		}
		faces = Collections.unmodifiableMap(copiedFaces);
		for (DynamicBlockModelElement element : elements) {
			for (DynamicBlockModelFace face : element.faces().values()) validateTextureReference(face, textureIds);
		}
	}

	public DynamicBlockTexture texture(String id) {
		for (DynamicBlockTexture texture : textures) if (texture.id().equals(id)) return texture;
		throw new IllegalArgumentException("Unknown block model texture: " + id);
	}

	public DynamicBlockTexture particleTextureAsset() {
		return texture(particleTexture);
	}

	public Set<String> textureHashes() {
		return textures.stream().map(DynamicBlockTexture::hash).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public void validateFor(DynamicBlockShape shape) {
		if (shape == DynamicBlockShape.CUSTOM && elements.isEmpty()) {
			throw new IllegalArgumentException("Custom block models require at least one cuboid element");
		}
		if (shape != DynamicBlockShape.CUSTOM && !elements.isEmpty()) {
			throw new IllegalArgumentException("Cuboid elements are used only with shape=custom");
		}
		for (DynamicBlockTexture texture : textures) {
			if (shape == DynamicBlockShape.TRANSLUCENT_CUBE || shape == DynamicBlockShape.NO_COLLISION) {
				continue;
			}
			if (shape == DynamicBlockShape.STAR || shape == DynamicBlockShape.CROSS
					|| shape == DynamicBlockShape.TORCH || shape == DynamicBlockShape.CUSTOM) {
				texture.texture().requireBinaryAlpha();
			} else {
				texture.texture().requireOpaque();
			}
		}
	}

	/** Entity cuboids share this model format but may use translucent textures and never define block collision. */
	public void validateForEntity() {
		if (elements.isEmpty()) throw new IllegalArgumentException("Custom entity models require at least one cuboid element");
	}

	private static void validateTextureReference(DynamicBlockModelFace face, Set<String> textureIds) {
		if (!textureIds.contains(face.texture())) {
			throw new IllegalArgumentException("Block model face refers to unknown texture: " + face.texture());
		}
	}
}
