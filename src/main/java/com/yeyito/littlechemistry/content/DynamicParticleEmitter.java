package com.yeyito.littlechemistry.content;

public record DynamicParticleEmitter(
		DynamicParticleType type,
		double chancePerTick,
		int count,
		double velocity,
		boolean topSurface
) {
	public DynamicParticleEmitter {
		if (type == null) {
			throw new IllegalArgumentException("Particle type is required");
		}
		if (!Double.isFinite(chancePerTick) || chancePerTick < 0.0 || chancePerTick > 0.25) {
			throw new IllegalArgumentException("Particle chance must be between 0 and 0.25");
		}
		if (count < 1 || count > 4) {
			throw new IllegalArgumentException("Particle count must be between 1 and 4");
		}
		if (!Double.isFinite(velocity) || velocity < 0.0 || velocity > 0.2) {
			throw new IllegalArgumentException("Particle velocity must be between 0 and 0.2");
		}
	}
}
