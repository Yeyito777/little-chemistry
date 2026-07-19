package com.yeyito.littlechemistry.particle;

import com.yeyito.littlechemistry.LittleChemistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** One bootstrap-registered carrier type for every logical AI-authored particle. */
public final class DynamicParticleRegistry {
	public static final ParticleType<DynamicParticleOptions> TYPE = Registry.register(
			BuiltInRegistries.PARTICLE_TYPE,
			LittleChemistry.id("dynamic"),
			FabricParticleTypes.complex(false,
					DynamicParticleOptions.MAP_CODEC, DynamicParticleOptions.STREAM_CODEC));

	private DynamicParticleRegistry() {
	}

	public static void register() {
		// Trigger static registration during common initialization.
	}
}
