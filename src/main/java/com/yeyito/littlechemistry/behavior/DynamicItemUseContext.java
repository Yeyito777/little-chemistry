package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

/** Direct, server-only context for right-clicking a generated stack in air. */
public record DynamicItemUseContext(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack stack,
		DynamicContentDefinition definition
) {
	public DynamicItemState state() {
		return DynamicItemState.of(stack);
	}

	public DynamicActionInput actionInput() {
		return DynamicActionInput.capture(player);
	}

	public ChargedProjectiles chargedProjectiles() {
		return stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
	}

	public boolean isCharged() {
		return !chargedProjectiles().isEmpty();
	}

	public void clearCharge() {
		stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
	}
}
