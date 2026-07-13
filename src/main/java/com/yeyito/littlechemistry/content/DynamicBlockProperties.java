package com.yeyito.littlechemistry.content;

import java.util.List;

public record DynamicBlockProperties(
		DynamicMaterial material,
		float hardness,
		DynamicTool preferredTool,
		boolean requiresCorrectTool,
		DynamicBlockShape shape,
		int redstonePower,
		int comparatorPower,
		int lightLevel,
		boolean visuallyEmissive,
		List<DynamicParticleEmitter> particles
) {
	public static final DynamicBlockProperties DEFAULT = new DynamicBlockProperties(
			DynamicMaterial.STONE, 1.5F, DynamicTool.NONE, false, DynamicBlockShape.FULL_CUBE,
			0, 0, 0, false, List.of()
	);

	public DynamicBlockProperties {
		if (material == null || preferredTool == null || shape == null) {
			throw new IllegalArgumentException("Block material, preferred tool, and shape are required");
		}
		if (!Float.isFinite(hardness) || hardness < 0.0F || hardness > 50.0F) {
			throw new IllegalArgumentException("Block hardness must be between 0 and 50");
		}
		if (redstonePower < 0 || redstonePower > 15 || comparatorPower < 0 || comparatorPower > 15) {
			throw new IllegalArgumentException("Block redstone and comparator power must be between 0 and 15");
		}
		if (lightLevel < 0 || lightLevel > 15) throw new IllegalArgumentException("Block light level must be between 0 and 15");
		particles = List.copyOf(particles);
		if (particles.size() > 2) throw new IllegalArgumentException("A block may have at most two particle emitters");
	}
}
