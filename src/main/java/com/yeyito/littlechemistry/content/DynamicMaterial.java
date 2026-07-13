package com.yeyito.littlechemistry.content;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.SoundType;

public enum DynamicMaterial implements StringRepresentable {
	STONE("stone", SoundType.STONE),
	METAL("metal", SoundType.METAL),
	WOOD("wood", SoundType.WOOD),
	GLASS("glass", SoundType.GLASS),
	CRYSTAL("crystal", SoundType.AMETHYST),
	EARTH("earth", SoundType.GRAVEL),
	ORGANIC("organic", SoundType.MOSS),
	CLOTH("cloth", SoundType.WOOL);

	private final String serializedName;
	private final SoundType soundType;

	DynamicMaterial(String serializedName, SoundType soundType) {
		this.serializedName = serializedName;
		this.soundType = soundType;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public SoundType soundType() {
		return soundType;
	}

	public static DynamicMaterial parse(String value) {
		for (DynamicMaterial material : values()) {
			if (material.serializedName.equals(value)) {
				return material;
			}
		}
		throw new IllegalArgumentException("Unknown material profile: " + value);
	}
}
