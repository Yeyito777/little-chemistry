package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the shared runtime item carriers honor the fuel duration of their logical definition. */
@Mixin(FuelValues.class)
public abstract class FuelValuesMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
		Integer duration = littleChemistry$dynamicBurnDuration(stack);
		if (duration != null) callback.setReturnValue(duration > 0);
	}

	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$burnDuration(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
		Integer duration = littleChemistry$dynamicBurnDuration(stack);
		if (duration != null) callback.setReturnValue(duration);
	}

	/** Null means an ordinary registered item whose native FuelValues entry should remain authoritative. */
	private static Integer littleChemistry$dynamicBurnDuration(ItemStack stack) {
		if (DynamicContentObjects.CONTENT_ID == null) return null;
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null) {
			return definition.item() == null ? 0 : definition.item().fuelBurnTicks();
		}
		return DynamicContentObjects.isCarrierItem(stack.getItem()) ? 0 : null;
	}
}
