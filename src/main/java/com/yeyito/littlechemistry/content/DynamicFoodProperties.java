package com.yeyito.littlechemistry.content;

import java.util.List;

public record DynamicFoodProperties(
		int hunger,
		float saturation,
		float consumeSeconds,
		boolean alwaysEdible,
		List<DynamicFoodEffect> effects,
		DynamicConsumeStyle style
) {
	/** Compatibility constructor for foods created before drink-style consumables were supported. */
	public DynamicFoodProperties(int hunger, float saturation, float consumeSeconds, boolean alwaysEdible,
			List<DynamicFoodEffect> effects) {
		this(hunger, saturation, consumeSeconds, alwaysEdible, effects, DynamicConsumeStyle.EAT);
	}

	public DynamicFoodProperties {
		if (style == null) throw new IllegalArgumentException("Food consume style is required");
		if (hunger < 0 || hunger > 20) throw new IllegalArgumentException("Hunger restoration must be between 0 and 20");
		if (!Float.isFinite(saturation) || saturation < 0.0F || saturation > 20.0F) {
			throw new IllegalArgumentException("Saturation restoration must be between 0 and 20");
		}
		if (!Float.isFinite(consumeSeconds) || consumeSeconds < 0.05F || consumeSeconds > 60.0F) {
			throw new IllegalArgumentException("Consume time must be between 0.05 and 60 seconds");
		}
		if (effects == null || effects.size() > 32) throw new IllegalArgumentException("Food may have at most 32 status effects");
		effects = List.copyOf(effects);
	}
}
