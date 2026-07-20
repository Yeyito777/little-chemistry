package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.Rarity;

public record DynamicContentDefinition(
		DynamicContentType type,
		String name,
		String displayName,
		String description,
		DynamicRarity rarityTier,
		long textureSeed,
		String textureHash,
		DynamicTextureSpec texture,
		String armorDisplayTextureHash,
		DynamicArmorDisplayTextureSpec armorDisplayTexture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		String behaviorSource,
		DynamicBlockModel blockModel,
		DynamicEntityProperties entity,
		DynamicEntityModel entityModel
) {
	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(type, name, displayName, "", DynamicRarity.fromProperties(block, item, null),
				textureSeed, textureHash, texture, null, null,
				block, item, null, behaviorSource, null, null, null);
	}

	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor, String behaviorSource) {
		this(type, name, displayName, "", DynamicRarity.fromProperties(block, item, armor),
				textureSeed, textureHash, texture, null, null,
				block, item, armor, behaviorSource, null, null, null);
	}

	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, String armorDisplayTextureHash,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor, String behaviorSource) {
		this(type, name, displayName, "", DynamicRarity.fromProperties(block, item, armor),
				textureSeed, textureHash, texture, armorDisplayTextureHash,
				armorDisplayTexture, block, item, armor, behaviorSource, null, null, null);
	}

	/** Compatibility constructor for callers predating generated descriptions. */
	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, long textureSeed,
			String textureHash, DynamicTextureSpec texture, String armorDisplayTextureHash,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor, String behaviorSource,
			DynamicBlockModel blockModel) {
		this(type, name, displayName, "", DynamicRarity.fromProperties(block, item, armor),
				textureSeed, textureHash, texture, armorDisplayTextureHash,
				armorDisplayTexture, block, item, armor, behaviorSource, blockModel, null, null);
	}

	public DynamicContentDefinition(DynamicContentType type, String name, String displayName, String description,
			DynamicRarity rarityTier, long textureSeed, String textureHash, DynamicTextureSpec texture,
			String armorDisplayTextureHash, DynamicArmorDisplayTextureSpec armorDisplayTexture,
			DynamicBlockProperties block, DynamicItemProperties item, DynamicArmorProperties armor,
			String behaviorSource, DynamicBlockModel blockModel) {
		this(type, name, displayName, description, rarityTier, textureSeed, textureHash, texture,
				armorDisplayTextureHash, armorDisplayTexture, block, item, armor, behaviorSource, blockModel,
				null, null);
	}

	public DynamicContentDefinition {
		if (name == null || !name.matches("[a-z0-9_]{1,64}")) {
			throw new IllegalArgumentException("Dynamic content ID is invalid");
		}
		if (displayName == null || displayName.isBlank() || displayName.length() > 64
				|| displayName.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Dynamic content display name is invalid");
		}
		description = normalizeDescription(description);
		if (rarityTier == null) throw new IllegalArgumentException("Dynamic content rarity is required");
		if (textureHash == null || !textureHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Dynamic content texture hash is invalid");
		}
		if ((armorDisplayTextureHash == null) != (armorDisplayTexture == null)) {
			throw new IllegalArgumentException("Armor display texture hash and specification must be supplied together");
		}
		if (armorDisplayTextureHash != null && !armorDisplayTextureHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Dynamic armor display texture hash is invalid");
		}
		if (behaviorSource == null || behaviorSource.isBlank()) {
			throw new IllegalArgumentException("Dynamic content requires Java behavior source");
		}
		behaviorSource = behaviorSource.strip();
		if (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Dynamic behavior source is invalid");
		}
		switch (type) {
			case BLOCK -> {
				if (block == null || item != null || armor != null || entity != null
						|| armorDisplayTexture != null || entityModel != null) {
					throw new IllegalArgumentException("Block content must have block properties only");
				}
				if (blockModel != null) {
					blockModel.validateFor(block.shape());
					DynamicBlockTexture primary = blockModel.particleTextureAsset();
					if (!primary.hash().equals(textureHash) || !primary.texture().equals(texture)) {
						throw new IllegalArgumentException("Block primary texture must match its model particle texture");
					}
				} else if (block.shape() == DynamicBlockShape.CUSTOM
						|| block.shape() == DynamicBlockShape.CROSS || block.shape() == DynamicBlockShape.TORCH) {
					throw new IllegalArgumentException("This block shape requires a runtime visual model");
				} else if (texture != null) {
					if (block.shape() == DynamicBlockShape.STAR) texture.requireBinaryAlpha();
					else texture.requireOpaque();
				}
			}
			case ITEM -> {
				if (item == null || block != null || armor != null || entity != null
						|| armorDisplayTexture != null || blockModel != null || entityModel != null) {
					throw new IllegalArgumentException("Item content must have item properties only");
				}
				if (texture != null) {
					texture.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
					texture.requireBinaryAlpha();
				}
			}
			case ARMOR -> {
				if (armor == null || block != null || item != null || entity != null
						|| blockModel != null || entityModel != null) {
					throw new IllegalArgumentException("Armor content must have armor properties only");
				}
				if (texture != null) {
					texture.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
					texture.requireBinaryAlpha();
				}
			}
			case ENTITY -> {
				if (entity == null || block != null || item != null || armor != null
						|| armorDisplayTexture != null || blockModel != null || entityModel == null) {
					throw new IllegalArgumentException("Entity content must have entity properties and a visual model only");
				}
				if (texture == null) throw new IllegalArgumentException("Entity content requires a spawner icon texture");
				texture.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
				texture.requireBinaryAlpha();
			}
		}
		if (entity == null && DynamicRarity.fromProperties(block, item, armor).vanillaRarity() != rarityTier.vanillaRarity()) {
			throw new IllegalArgumentException("Dynamic rarity does not match the content's vanilla rarity component");
		}
	}

	public String idPath() {
		return name;
	}

	public Rarity rarity() {
		return rarityTier.vanillaRarity();
	}

	public static String normalizeDescription(String raw) {
		String normalized = raw == null ? "" : raw.strip();
		if (normalized.length() > 120 || normalized.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Description must contain at most 120 printable characters");
		}
		return normalized;
	}

	/**
	 * Legacy armor definitions did not store separate worn artwork and continue to use their icon hash
	 * until they are recreated. New definitions always return the dedicated 64x32 asset hash.
	 */
	public String effectiveArmorDisplayTextureHash() {
		if (type != DynamicContentType.ARMOR) {
			throw new IllegalStateException("Only armor has an equipped display texture");
		}
		return armorDisplayTextureHash == null ? textureHash : armorDisplayTextureHash;
	}

	/** All ordinary textures needed to render this definition, excluding the separate worn-armor sheet. */
	public java.util.Set<String> renderTextureHashes() {
		if (blockModel != null) return blockModel.textureHashes();
		if (entityModel == null) return java.util.Set.of(textureHash);
		java.util.Set<String> hashes = new java.util.HashSet<>(entityModel.textureHashes());
		hashes.add(textureHash);
		return java.util.Set.copyOf(hashes);
	}
}
