package com.yeyito.littlechemistry.content;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record DynamicFoodEffect(
		Identifier effect,
		float durationSeconds,
		int amplifier,
		float probability,
		boolean ambient,
		boolean showParticles,
		boolean showIcon
) {
	public DynamicFoodEffect {
		if (effect == null || BuiltInRegistries.MOB_EFFECT.get(effect).isEmpty()) {
			throw new IllegalArgumentException("Unknown Minecraft status effect: " + effect);
		}
		if (!Float.isFinite(durationSeconds) || durationSeconds < 0.05F || durationSeconds > 86_400.0F) {
			throw new IllegalArgumentException("Food effect duration must be between 0.05 and 86400 seconds");
		}
		if (amplifier < 0 || amplifier > 255) throw new IllegalArgumentException("Food effect amplifier must be between 0 and 255");
		if (!Float.isFinite(probability) || probability < 0.0F || probability > 1.0F) {
			throw new IllegalArgumentException("Food effect probability must be between 0 and 1");
		}
	}

	public MobEffectInstance instance() {
		Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(effect)
				.orElseThrow(() -> new IllegalStateException("Status effect disappeared from the registry: " + effect));
		int ticks = Math.max(1, Math.round(durationSeconds * 20.0F));
		return new MobEffectInstance(holder, ticks, amplifier, ambient, showParticles, showIcon);
	}
}
