package com.yeyito.littlechemistry.content;

import java.util.Objects;

/** Required authored effects for the AI invention and ready-to-take phases of a generated workstation. */
public final class DynamicWorkstationParticles {
	static final DynamicWorkstationParticles LEGACY_DEFAULTS = new DynamicWorkstationParticles(
			new DynamicWorkstationParticleEffect("crit", 2, 0.32, 0.08, 0.04),
			new DynamicWorkstationParticleEffect("happy_villager", 2, 0.32, 0.08, 0.04), false);

	private final DynamicWorkstationParticleEffect inventing;
	private final DynamicWorkstationParticleEffect ready;
	private final boolean authored;

	public DynamicWorkstationParticles(DynamicWorkstationParticleEffect inventing,
			DynamicWorkstationParticleEffect ready) {
		this(inventing, ready, true);
	}

	private DynamicWorkstationParticles(DynamicWorkstationParticleEffect inventing,
			DynamicWorkstationParticleEffect ready, boolean authored) {
		this.inventing = Objects.requireNonNull(inventing,
				"Workstations require an inventing particle effect");
		this.ready = Objects.requireNonNull(ready,
				"Workstations require a ready particle effect");
		this.authored = authored;
	}

	public DynamicWorkstationParticleEffect inventing() {
		return inventing;
	}

	public DynamicWorkstationParticleEffect ready() {
		return ready;
	}

	/** Distinguishes the compatibility constructor from an author deliberately choosing the same native effects. */
	public boolean isLegacyFallback() {
		return !authored;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof DynamicWorkstationParticles particles
				&& authored == particles.authored
				&& inventing.equals(particles.inventing)
				&& ready.equals(particles.ready);
	}

	@Override
	public int hashCode() {
		return Objects.hash(inventing, ready, authored);
	}

	@Override
	public String toString() {
		return "DynamicWorkstationParticles[inventing=" + inventing + ", ready=" + ready
				+ ", authored=" + authored + "]";
	}
}
