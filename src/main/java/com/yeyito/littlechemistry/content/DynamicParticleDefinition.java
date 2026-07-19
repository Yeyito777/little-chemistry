package com.yeyito.littlechemistry.content;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A bounded client-rendered particle visual owned by one generated content definition. */
public record DynamicParticleDefinition(
		String id,
		List<DynamicParticleFrame> frames,
		int frameTicks,
		boolean loop,
		int lifetimeTicks,
		float startSize,
		float endSize,
		String startColor,
		String endColor,
		float gravity,
		float friction,
		boolean collision,
		boolean emissive,
		float spin
) {
	public static final int MAX_DEFINITIONS = 4;
	public static final int MAX_FRAMES = 4;
	public static final int MAX_FRAME_DIMENSION = 32;
	public static final int MAX_LIFETIME_TICKS = 400;

	public DynamicParticleDefinition {
		if (id == null || !id.matches("[a-z][a-z0-9_]{0,31}")) {
			throw new IllegalArgumentException("Dynamic particle ID is invalid");
		}
		frames = List.copyOf(frames);
		if (frames.isEmpty() || frames.size() > MAX_FRAMES) {
			throw new IllegalArgumentException("A dynamic particle must contain 1-" + MAX_FRAMES + " frames");
		}
		int width = frames.getFirst().texture().width();
		int height = frames.getFirst().texture().height();
		if (width != height || width > MAX_FRAME_DIMENSION) {
			throw new IllegalArgumentException("Dynamic particle frames must be square and at most "
					+ MAX_FRAME_DIMENSION + "x" + MAX_FRAME_DIMENSION + " pixels");
		}
		for (DynamicParticleFrame frame : frames) {
			if (frame.texture().width() != width || frame.texture().height() != height) {
				throw new IllegalArgumentException("Every frame of a dynamic particle must have matching dimensions");
			}
			if (!hasVisiblePixel(frame.texture())) {
				throw new IllegalArgumentException("Dynamic particle frames must contain a visible pixel");
			}
		}
		if (frameTicks < 1 || frameTicks > 40) {
			throw new IllegalArgumentException("Dynamic particle frameTicks must be between 1 and 40");
		}
		if (lifetimeTicks < 1 || lifetimeTicks > MAX_LIFETIME_TICKS) {
			throw new IllegalArgumentException("Dynamic particle lifetime must be between 1 and "
					+ MAX_LIFETIME_TICKS + " ticks");
		}
		int minimumAnimatedLifetime = (frames.size() - 1) * frameTicks + 1;
		if (lifetimeTicks < minimumAnimatedLifetime) {
			throw new IllegalArgumentException("Dynamic particle lifetime must be at least "
					+ minimumAnimatedLifetime + " ticks for every animation frame to render");
		}
		if (!Float.isFinite(startSize) || !Float.isFinite(endSize)
				|| startSize < 0.01F || startSize > 4.0F || endSize < 0.01F || endSize > 4.0F) {
			throw new IllegalArgumentException("Dynamic particle sizes must be between 0.01 and 4");
		}
		startColor = normalizeColor(startColor);
		endColor = normalizeColor(endColor);
		if (channel(startColor, 6) == 0.0F && channel(endColor, 6) == 0.0F) {
			throw new IllegalArgumentException("Dynamic particle tint must be visible during its lifetime");
		}
		if (!Float.isFinite(gravity) || gravity < -2.0F || gravity > 2.0F) {
			throw new IllegalArgumentException("Dynamic particle gravity must be between -2 and 2");
		}
		if (!Float.isFinite(friction) || friction < 0.0F || friction > 1.0F) {
			throw new IllegalArgumentException("Dynamic particle friction must be between 0 and 1");
		}
		if (!Float.isFinite(spin) || spin < -1.0F || spin > 1.0F) {
			throw new IllegalArgumentException("Dynamic particle spin must be between -1 and 1 radians per tick");
		}
	}

	public Set<String> textureHashes() {
		Set<String> hashes = new HashSet<>();
		for (DynamicParticleFrame frame : frames) hashes.add(frame.textureHash());
		return Set.copyOf(hashes);
	}

	static List<DynamicParticleDefinition> validateLibrary(List<DynamicParticleDefinition> particles) {
		if (particles == null) throw new IllegalArgumentException("Custom particle library is required");
		particles = List.copyOf(particles);
		if (particles.size() > MAX_DEFINITIONS) {
			throw new IllegalArgumentException("Dynamic content may define at most " + MAX_DEFINITIONS
					+ " custom particles");
		}
		Set<String> ids = new HashSet<>();
		for (DynamicParticleDefinition particle : particles) {
			if (!ids.add(particle.id())) {
				throw new IllegalArgumentException("Duplicate custom particle ID: " + particle.id());
			}
		}
		return particles;
	}

	static void validateAmbientEmitters(DynamicBlockProperties block,
			List<DynamicParticleDefinition> particles) {
		if (block == null) return;
		java.util.Map<String, DynamicParticleDefinition> byId = particles.stream().collect(
				java.util.stream.Collectors.toMap(DynamicParticleDefinition::id, particle -> particle));
		double expectedLiveParticles = 0.0;
		for (DynamicParticleEmitter emitter : block.particles()) {
			if (!emitter.custom()) continue;
			DynamicParticleDefinition particle = byId.get(emitter.customParticleId());
			if (particle == null) {
				throw new IllegalArgumentException("Block emitter references undefined custom particle: "
						+ emitter.customParticleId());
			}
			expectedLiveParticles += emitter.chancePerTick() * emitter.count() * particle.lifetimeTicks();
		}
		if (expectedLiveParticles > 64.0) {
			throw new IllegalArgumentException(
					"Custom block particle emitters exceed the 64 expected-live-particle budget");
		}
	}

	public float startRed() {
		return channel(startColor, 0);
	}

	public float startGreen() {
		return channel(startColor, 2);
	}

	public float startBlue() {
		return channel(startColor, 4);
	}

	public float startAlpha() {
		return channel(startColor, 6);
	}

	public float endRed() {
		return channel(endColor, 0);
	}

	public float endGreen() {
		return channel(endColor, 2);
	}

	public float endBlue() {
		return channel(endColor, 4);
	}

	public float endAlpha() {
		return channel(endColor, 6);
	}

	private static String normalizeColor(String color) {
		if (color == null || !color.matches("[0-9A-Fa-f]{8}")) {
			throw new IllegalArgumentException("Dynamic particle colors must use RRGGBBAA hexadecimal notation");
		}
		return color.toUpperCase(java.util.Locale.ROOT);
	}

	private static float channel(String color, int offset) {
		return Integer.parseInt(color.substring(offset, offset + 2), 16) / 255.0F;
	}

	private static boolean hasVisiblePixel(DynamicTextureSpec texture) {
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				int paletteIndex = Character.digit(row.charAt(index), 16);
				String color = texture.palette().get(paletteIndex);
				if (Integer.parseInt(color.substring(6, 8), 16) != 0) return true;
			}
		}
		return false;
	}
}
