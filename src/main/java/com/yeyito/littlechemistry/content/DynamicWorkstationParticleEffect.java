package com.yeyito.littlechemistry.content;

/** One bounded particle effect emitted by the engine for a workstation invention lifecycle state. */
public record DynamicWorkstationParticleEffect(
		String particle,
		int count,
		double horizontalSpread,
		double verticalSpread,
		double speed
) {
	public DynamicWorkstationParticleEffect {
		particle = DynamicParticleEmitter.normalizeParticle(particle);
		if (count < 1 || count > 8) {
			throw new IllegalArgumentException("Workstation particle count must be between 1 and 8");
		}
		if (!Double.isFinite(horizontalSpread) || horizontalSpread < 0.0 || horizontalSpread > 1.0
				|| !Double.isFinite(verticalSpread) || verticalSpread < 0.0 || verticalSpread > 1.0) {
			throw new IllegalArgumentException("Workstation particle spreads must be between 0 and 1");
		}
		if (!Double.isFinite(speed) || speed < 0.0 || speed > 0.5) {
			throw new IllegalArgumentException("Workstation particle speed must be between 0 and 0.5");
		}
	}

	public boolean custom() {
		return particle.startsWith("custom:");
	}

	public String customParticleId() {
		if (!custom()) throw new IllegalStateException("This effect uses a vanilla particle");
		return particle.substring("custom:".length());
	}

	/** Returns the vanilla profile, or {@code null} for a generated custom particle. */
	public DynamicParticleType type() {
		return custom() ? null : DynamicParticleType.parse(particle);
	}
}
