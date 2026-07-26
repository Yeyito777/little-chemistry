package com.yeyito.littlechemistry.content;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public enum DynamicParticleType {
	CRIT("crit"),
	HAPPY_VILLAGER("happy_villager"),
	SMOKE("smoke"),
	FLAME("flame"),
	PORTAL("portal"),
	ENCHANT("enchant"),
	END_ROD("end_rod"),
	ELECTRIC_SPARK("electric_spark"),
	GLOW("glow");

	private final String serializedName;

	DynamicParticleType(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public ParticleOptions particle() {
		return switch (this) {
			case CRIT -> ParticleTypes.CRIT;
			case HAPPY_VILLAGER -> ParticleTypes.HAPPY_VILLAGER;
			case SMOKE -> ParticleTypes.SMOKE;
			case FLAME -> ParticleTypes.FLAME;
			case PORTAL -> ParticleTypes.PORTAL;
			case ENCHANT -> ParticleTypes.ENCHANT;
			case END_ROD -> ParticleTypes.END_ROD;
			case ELECTRIC_SPARK -> ParticleTypes.ELECTRIC_SPARK;
			case GLOW -> ParticleTypes.GLOW;
		};
	}

	public static DynamicParticleType parse(String value) {
		for (DynamicParticleType type : values()) {
			if (type.serializedName.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown particle profile: " + value);
	}
}
