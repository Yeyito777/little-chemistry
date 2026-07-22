package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;

import java.util.List;

public record GeneratedContentSpec(
		DynamicTextureSpec texture,
		DynamicBlockProperties block,
		DynamicItemProperties item,
		DynamicArmorProperties armor,
		DynamicArmorDisplayTextureSpec armorDisplayTexture,
		String behaviorSource,
		DynamicBlockModel blockModel,
		DynamicRarity rarityTier,
		String description,
		List<DynamicParticleDefinition> customParticles,
		DynamicWorkstationSpec workstation,
		DynamicEntityProperties entity,
		DynamicEntityModel entityModel,
		DynamicItemVisuals itemVisuals
) {
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, String behaviorSource) {
		this(texture, block, item, null, null, behaviorSource, null,
					DynamicRarity.fromProperties(block, item, null), "", List.of(), null, null, null,
					DynamicItemVisuals.NONE);
	}

	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, null,
					DynamicRarity.fromProperties(block, item, armor), "", List.of(), null, null, null,
					DynamicItemVisuals.NONE);
	}

	/** Compatibility constructor for callers predating generated descriptions. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
					DynamicRarity.fromProperties(block, item, armor), "", List.of(), null, null, null,
					DynamicItemVisuals.NONE);
	}

	/** Compatibility constructor for callers predating generated custom particles. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel, DynamicRarity rarityTier, String description) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
					rarityTier, description, List.of(), null, null, null, DynamicItemVisuals.NONE);
	}

	/** Compatibility constructor for callers predating optional workstation blocks. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel, DynamicRarity rarityTier, String description,
			List<DynamicParticleDefinition> customParticles) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
					rarityTier, description, customParticles, null, null, null, DynamicItemVisuals.NONE);
	}

	/** Compatibility constructor for workstation-aware callers predating generated entities. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel, DynamicRarity rarityTier, String description,
			List<DynamicParticleDefinition> customParticles, DynamicWorkstationSpec workstation) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
					rarityTier, description, customParticles, workstation, null, null, DynamicItemVisuals.NONE);
	}

	/** Compatibility constructor for generated entities from the standalone entity subsystem. */
	public GeneratedContentSpec(DynamicTextureSpec texture, DynamicBlockProperties block,
			DynamicItemProperties item, DynamicArmorProperties armor,
			DynamicArmorDisplayTextureSpec armorDisplayTexture, String behaviorSource,
			DynamicBlockModel blockModel, DynamicRarity rarityTier, String description,
			DynamicEntityProperties entity, DynamicEntityModel entityModel) {
		this(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
					rarityTier, description, List.of(), null, entity, entityModel, DynamicItemVisuals.NONE);
	}

	public GeneratedContentSpec {
		if (texture == null) {
			throw new IllegalArgumentException("Generated content requires a texture");
		}
		int propertyKinds = (block == null ? 0 : 1) + (item == null ? 0 : 1)
				+ (armor == null ? 0 : 1) + (entity == null ? 0 : 1);
		if (propertyKinds != 1) {
			throw new IllegalArgumentException("Generated content must contain exactly one property kind");
		}
		if (rarityTier == null) throw new IllegalArgumentException("Generated content requires a rarity");
		itemVisuals = itemVisuals == null ? DynamicItemVisuals.NONE : itemVisuals;
		if (item == null) {
			if (!itemVisuals.isEmpty()) throw new IllegalArgumentException("Only generated items may have item visual states");
		} else {
			itemVisuals.validateFor(item.heldType());
		}
		customParticles = DynamicParticleDefinition.validateLibrary(customParticles);
		if (workstation != null && block == null) {
			throw new IllegalArgumentException("Only generated blocks may define a workstation");
		}
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
		}
		if ((armor == null) != (armorDisplayTexture == null)) {
			throw new IllegalArgumentException("Generated armor requires a separate 64x32 display texture");
		}
		if (entity == null
				&& DynamicRarity.fromProperties(block, item, armor).vanillaRarity() != rarityTier.vanillaRarity()) {
			throw new IllegalArgumentException("Generated rarity does not match the content's vanilla rarity component");
		}
		if (entity == null && entityModel != null) {
			throw new IllegalArgumentException("Only generated entities may have an entity model");
		}
		if (entity != null) {
			if (armorDisplayTexture != null) throw new IllegalArgumentException("Entities cannot have armor display textures");
			if (entityModel == null) throw new IllegalArgumentException("Generated entities require a visual model");
		}
		if (behaviorSource == null || behaviorSource.isBlank()) {
			throw new IllegalArgumentException("Generated content requires Java behavior source");
		}
		description = DynamicContentDefinition.normalizeDescription(description);
		behaviorSource = behaviorSource.strip();
		if (behaviorSource.length() > 65_536 || behaviorSource.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Generated behavior source is invalid");
		}
		if (item != null && item.heldType().requiresDurability()) {
			if (item.durability() < 1) {
				throw new IllegalArgumentException("Generated " + item.heldType().serializedName()
						+ " items require positive durability");
			}
		}
		if (item != null && item.heldType().requiresVisualStates()) {
			itemVisuals.requireCompleteFor(item.heldType());
		}
		boolean workstationBehavior = DynamicBehaviorSource.supports(
				behaviorSource, DynamicBehaviorCapability.WORKSTATION);
		if ((workstation != null) != workstationBehavior) {
			throw new IllegalArgumentException(workstation == null
					? "Ordinary generated content must not implement WorkstationBehavior"
					: "Generated workstations must implement WorkstationBehavior");
		}
		if (workstation != null && !DynamicBehaviorSource.supports(
				behaviorSource, DynamicBehaviorCapability.WORKSTATION_TICK)) {
			throw new IllegalArgumentException("Generated workstations must implement WorkstationTickBehavior");
		}
		DynamicParticleDefinition.validateAmbientEmitters(block, customParticles);
	}
}
