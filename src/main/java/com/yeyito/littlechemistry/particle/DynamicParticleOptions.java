package com.yeyito.littlechemistry.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Native particle options carrying only the synchronized content/effect lookup key. */
public record DynamicParticleOptions(
		Identifier contentId,
		String particleId
) implements ParticleOptions {
	private static final Codec<String> PARTICLE_ID_CODEC = Codec.STRING.validate(value ->
			validParticleId(value) ? DataResult.success(value)
					: DataResult.error(() -> "Invalid Little Chemistry custom particle ID"));
	public static final MapCodec<DynamicParticleOptions> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					Identifier.CODEC.fieldOf("content_id").forGetter(DynamicParticleOptions::contentId),
					PARTICLE_ID_CODEC.fieldOf("particle_id").forGetter(DynamicParticleOptions::particleId)
			).apply(instance, DynamicParticleOptions::new));
	public static final StreamCodec<ByteBuf, DynamicParticleOptions> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, DynamicParticleOptions::contentId,
			ByteBufCodecs.stringUtf8(32), DynamicParticleOptions::particleId,
			DynamicParticleOptions::new);

	public DynamicParticleOptions {
		if (contentId == null || !LittleChemistry.MOD_ID.equals(contentId.getNamespace())
				|| !contentId.getPath().matches("[a-z0-9_]{1,64}")) {
			throw new IllegalArgumentException("Dynamic particle content ID is invalid");
		}
		if (!validParticleId(particleId)) {
			throw new IllegalArgumentException("Dynamic particle ID is invalid");
		}
	}

	public static DynamicParticleOptions of(DynamicContentDefinition definition, String particleId) {
		if (definition == null || definition.customParticle(particleId) == null) {
			throw new IllegalArgumentException("Undefined custom particle '" + particleId + "'");
		}
		return new DynamicParticleOptions(LittleChemistry.id(definition.name()), particleId);
	}

	@Override
	public ParticleType<?> getType() {
		return DynamicParticleRegistry.TYPE;
	}

	private static boolean validParticleId(String value) {
		return value != null && value.matches("[a-z][a-z0-9_]{0,31}");
	}
}
