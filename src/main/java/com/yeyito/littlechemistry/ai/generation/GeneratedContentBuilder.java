package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorProperties;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicEntityModel;
import com.yeyito.littlechemistry.content.DynamicEntityProperties;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemVisuals;
import com.yeyito.littlechemistry.content.DynamicParticleDefinition;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationSpec;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;

import java.util.List;

/** A composable convenience builder available to world-authored generated Java. */
public final class GeneratedContentBuilder {
	private DynamicTextureSpec texture;
	private DynamicBlockProperties block;
	private DynamicItemProperties item;
	private DynamicArmorProperties armor;
	private DynamicArmorDisplayTextureSpec armorDisplayTexture;
	private DynamicBlockModel blockModel;
	private DynamicRarity rarity;
	private String description = "";
	private List<DynamicParticleDefinition> particles = List.of();
	private DynamicWorkstationSpec workstation;
	private DynamicEntityProperties entity;
	private DynamicEntityModel entityModel;
	private DynamicItemVisuals itemVisuals = DynamicItemVisuals.NONE;

	private GeneratedContentBuilder() {
	}

	public static GeneratedContentBuilder create() {
		return new GeneratedContentBuilder();
	}

	public GeneratedContentBuilder texture(DynamicTextureSpec value) { texture = value; return this; }
	public GeneratedContentBuilder block(DynamicBlockProperties value) { block = value; return this; }
	public GeneratedContentBuilder item(DynamicItemProperties value) { item = value; return this; }
	public GeneratedContentBuilder armor(DynamicArmorProperties value) { armor = value; return this; }
	public GeneratedContentBuilder armorDisplayTexture(DynamicArmorDisplayTextureSpec value) {
		armorDisplayTexture = value;
		return this;
	}
	public GeneratedContentBuilder blockModel(DynamicBlockModel value) { blockModel = value; return this; }
	public GeneratedContentBuilder rarity(DynamicRarity value) { rarity = value; return this; }
	public GeneratedContentBuilder description(String value) { description = value; return this; }
	public GeneratedContentBuilder particles(List<DynamicParticleDefinition> value) { particles = value; return this; }
	public GeneratedContentBuilder workstation(DynamicWorkstationSpec value) { workstation = value; return this; }
	public GeneratedContentBuilder entity(DynamicEntityProperties value) { entity = value; return this; }
	public GeneratedContentBuilder entityModel(DynamicEntityModel value) { entityModel = value; return this; }
	public GeneratedContentBuilder itemVisuals(DynamicItemVisuals value) { itemVisuals = value; return this; }

	public GeneratedContentSpec build(String behaviorSource) {
		return new GeneratedContentSpec(texture, block, item, armor, armorDisplayTexture, behaviorSource, blockModel,
				rarity, description, particles, workstation, entity, entityModel, itemVisuals);
	}
}
