package com.yeyito.littlechemistry.particle;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stable server-side API through which generated behavior emits generated particle visuals. */
public final class DynamicParticles {
	private static final Set<String> warnedMissingParticles = ConcurrentHashMap.newKeySet();

	private DynamicParticles() {
	}

	/** Emits one particle with exact initial motion through Minecraft's native particle packet. */
	public static int spawn(ServerLevel level, DynamicContentDefinition definition, String particleId,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		if (level == null) throw new IllegalArgumentException("Level is required");
		if (!finite(x, y, z, velocityX, velocityY, velocityZ)
				|| Math.abs(velocityX) > 4.0 || Math.abs(velocityY) > 4.0 || Math.abs(velocityZ) > 4.0) {
			throw new IllegalArgumentException("Dynamic particle position or velocity is out of range");
		}
		DynamicParticleOptions options = options(definition, particleId);
		if (options == null) return 0;
		return level.sendParticles(options,
				x, y, z, 0, velocityX, velocityY, velocityZ, 1.0);
	}

	/**
	 * Emits a native Minecraft particle burst. Axis spreads randomize positions and speed controls randomized
	 * motion in exactly the same way as {@link ServerLevel#sendParticles}.
	 */
	public static int spawn(ServerLevel level, DynamicContentDefinition definition, String particleId,
			double x, double y, double z, int count,
			double spreadX, double spreadY, double spreadZ, double speed) {
		if (level == null || definition == null) throw new IllegalArgumentException("Level and definition are required");
		if (count < 1 || count > 128 || !finite(x, y, z, spreadX, spreadY, spreadZ, speed)
				|| spreadX < 0.0 || spreadX > 16.0 || spreadY < 0.0 || spreadY > 16.0
				|| spreadZ < 0.0 || spreadZ > 16.0 || speed < 0.0 || speed > 4.0) {
			throw new IllegalArgumentException("Dynamic particle burst values are out of range");
		}
		DynamicParticleOptions options = options(definition, particleId);
		if (options == null) return 0;
		return level.sendParticles(options,
				x, y, z, count, spreadX, spreadY, spreadZ, speed);
	}

	private static DynamicParticleOptions options(DynamicContentDefinition definition, String particleId) {
		if (definition != null && definition.customParticle(particleId) != null) {
			return DynamicParticleOptions.of(definition, particleId);
		}
		String key = (definition == null ? "<missing definition>" : definition.name()) + ":" + particleId;
		if (warnedMissingParticles.add(key)) {
			LittleChemistry.LOGGER.warn("Generated behavior tried to emit undefined custom particle {}", key);
		}
		return null;
	}

	private static boolean finite(double... values) {
		for (double value : values) if (!Double.isFinite(value)) return false;
		return true;
	}
}
