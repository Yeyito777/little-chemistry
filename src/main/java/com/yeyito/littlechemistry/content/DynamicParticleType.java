package com.yeyito.littlechemistry.content;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public enum DynamicParticleType {
	SMOKE("smoke", ParticleTypes.SMOKE),
	FLAME("flame", ParticleTypes.FLAME),
	PORTAL("portal", ParticleTypes.PORTAL),
	ENCHANT("enchant", ParticleTypes.ENCHANT),
	END_ROD("end_rod", ParticleTypes.END_ROD),
	ELECTRIC_SPARK("electric_spark", ParticleTypes.ELECTRIC_SPARK),
	GLOW("glow", ParticleTypes.GLOW);

	private final String serializedName;
	private final ParticleOptions particle;

	DynamicParticleType(String serializedName, ParticleOptions particle) {
		this.serializedName = serializedName;
		this.particle = particle;
	}

	public String serializedName() {
		return serializedName;
	}

	public ParticleOptions particle() {
		return particle;
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
