package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.List;

/** Authoritative state for held-use ticks and release callbacks on a generated item. */
public record DynamicItemUsingContext(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack stack,
		DynamicContentDefinition definition,
		int useTicks,
		int remainingUseTicks,
		int useDurationTicks,
		int chargeDurationTicks
) {
	public DynamicItemUsingContext {
		if (useTicks < 0 || remainingUseTicks < 0 || useDurationTicks < 1 || chargeDurationTicks < 1) {
			throw new IllegalArgumentException("Generated item use timing is invalid");
		}
	}

	public DynamicActionInput actionInput() {
		return DynamicActionInput.capture(player);
	}

	public DynamicItemState state() {
		return DynamicItemState.of(stack);
	}

	public float chargeProgress() {
		return Math.clamp((float)useTicks / chargeDurationTicks, 0.0F, 1.0F);
	}

	public boolean fullyCharged() {
		return useTicks >= chargeDurationTicks;
	}

	public ChargedProjectiles chargedProjectiles() {
		return stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
	}

	public boolean isCharged() {
		return !chargedProjectiles().isEmpty();
	}

	/** Stores one native charged projectile component, enabling crossbow charged rendering and persistence. */
	public void charge(ItemStack projectile) {
		if (projectile == null || projectile.isEmpty()) {
			throw new IllegalArgumentException("A charged projectile must not be empty");
		}
		stack.set(DataComponents.CHARGED_PROJECTILES,
				ChargedProjectiles.ofNonEmpty(List.of(projectile.copyWithCount(1))));
	}

	public void clearCharge() {
		stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
	}
}
