package com.yeyito.littlechemistry.content;

public record DynamicParticleEmitter(
		String particle,
		double chancePerTick,
		int count,
		double velocity,
		boolean topSurface
) {
	public DynamicParticleEmitter {
		particle = normalizeParticle(particle);
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

	public DynamicParticleEmitter(DynamicParticleType type, double chancePerTick, int count,
			double velocity, boolean topSurface) {
		this(type == null ? null : type.serializedName(), chancePerTick, count, velocity, topSurface);
	}

	public boolean custom() {
		return particle.startsWith("custom:");
	}

	public String customParticleId() {
		if (!custom()) throw new IllegalStateException("This emitter uses a vanilla particle");
		return particle.substring("custom:".length());
	}

	/** Returns the vanilla profile, or {@code null} for a generated custom particle. */
	public DynamicParticleType type() {
		return custom() ? null : DynamicParticleType.parse(particle);
	}

	private static String normalizeParticle(String value) {
		if (value == null) throw new IllegalArgumentException("Particle is required");
		String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
		if (normalized.startsWith("custom:")) {
			String id = normalized.substring("custom:".length());
			if (!id.matches("[a-z][a-z0-9_]{0,31}")) {
				throw new IllegalArgumentException("Custom particle reference is invalid");
			}
			return "custom:" + id;
		}
		DynamicParticleType.parse(normalized);
		return normalized;
	}
}
