package com.yeyito.littlechemistry.content;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		DynamicArmorDisplayTextureSpec armorDisplayTexture,
		String behaviorSource,
		DynamicBlockModel blockModel,
		DynamicRarity rarityTier,
		String description
) {
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(texture, block, item, null, null, behaviorSource, null,
				DynamicRarity.fromProperties(block, item, null), "");
	}

	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, null,
				DynamicRarity.fromProperties(block, item, armor), "");
	}

	/** Compatibility constructor for callers predating generated descriptions. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
				DynamicRarity.fromProperties(block, item, armor), "");
	}

	public GeneratedContentSpec {
		if (texture == null) {
			throw new IllegalArgumentException("Generated content requires a texture");
		}
		int propertyKinds = (block == null ? 0 : 1) + (item == null ? 0 : 1) + (armor == null ? 0 : 1);
		if (propertyKinds != 1) {
			throw new IllegalArgumentException("Generated content must contain exactly one property kind");
		}
		if (rarityTier == null) throw new IllegalArgumentException("Generated content requires a rarity");
		if (block != null) {
			if (blockModel == null) throw new IllegalArgumentException("Generated blocks require a visual model");
			blockModel.validateFor(block.shape());
			DynamicBlockTexture primary = blockModel.particleTextureAsset();
			if (!primary.texture().equals(texture)) {
				throw new IllegalArgumentException("Generated block primary texture must match its particle texture");
			}
		} else {
			if (blockModel != null) throw new IllegalArgumentException("Only generated blocks may have a block model");
			texture.requireDimensions(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT);
			texture.requireBinaryAlpha();
		}
		if ((armor == null) != (armorDisplayTexture == null)) {
			throw new IllegalArgumentException("Generated armor requires a separate 64x32 display texture");
		}
		if (DynamicRarity.fromProperties(block, item, armor).vanillaRarity() != rarityTier.vanillaRarity()) {
			throw new IllegalArgumentException("Generated rarity does not match the content's vanilla rarity component");
		}
		if (behaviorSource == null || behaviorSource.isBlank()) {
			throw new IllegalArgumentException("Generated content requires Java behavior source");
		}
		description = DynamicContentDefinition.normalizeDescription(description);
		behaviorSource = behaviorSource.strip();
		if (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Generated behavior source is invalid");
		}
	}
}
