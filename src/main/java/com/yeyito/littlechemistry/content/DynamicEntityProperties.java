package com.yeyito.littlechemistry.content;

import net.minecraft.resources.Identifier;

import java.util.List;

/** Declarative, world-persistent native mechanics for a generated carrier mob. */
public record DynamicEntityProperties(
		DynamicEntityMovement movement,
		DynamicEntityDisposition disposition,
		float width,
		float height,
		float eyeHeight,
		double maxHealth,
		double movementSpeed,
		double attackDamage,
		double armor,
		double knockbackResistance,
		double followRange,
		int experienceReward,
		boolean fireImmune,
		Identifier ambientSound,
		Identifier hurtSound,
		Identifier deathSound,
		List<DynamicEntityDrop> drops
) {
	public DynamicEntityProperties {
		if (movement == null || disposition == null) {
			throw new IllegalArgumentException("Entity movement and disposition are required");
		}
		if (!Float.isFinite(width) || width < 0.1F || width > 8.0F
				|| !Float.isFinite(height) || height < 0.1F || height > 8.0F
				|| !Float.isFinite(eyeHeight) || eyeHeight < 0.0F || eyeHeight > height) {
			throw new IllegalArgumentException("Entity dimensions are invalid");
		}
		requireRange(maxHealth, 1.0, 1024.0, "maxHealth");
		requireRange(movementSpeed, 0.0, 2.0, "movementSpeed");
		requireRange(attackDamage, 0.0, 2048.0, "attackDamage");
		requireRange(armor, 0.0, 30.0, "armor");
		requireRange(knockbackResistance, 0.0, 1.0, "knockbackResistance");
		requireRange(followRange, 1.0, 128.0, "followRange");
		if (experienceReward < 0 || experienceReward > 10_000) {
			throw new IllegalArgumentException("experienceReward must be between 0 and 10000");
		}
		if (ambientSound == null || hurtSound == null || deathSound == null) {
			throw new IllegalArgumentException("Entity sounds are required");
		}
		drops = List.copyOf(drops == null ? List.of() : drops);
		if (drops.size() > 16) throw new IllegalArgumentException("Entities may define at most 16 drops");
	}

	private static void requireRange(double value, double minimum, double maximum, String name) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
		}
	}
}
