package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.content.DynamicCarrierHooks;
import com.yeyito.littlechemistry.content.DynamicItemCarrier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Keeps logical names/tooltips shared even when the registered carrier has a native superclass. */
@Mixin(Item.class)
public abstract class DynamicItemPresentationMixin {
	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$dynamicName(ItemStack stack,
			CallbackInfoReturnable<Component> callback) {
		if (!((Object) this instanceof DynamicItemCarrier)) return;
		Component name = DynamicCarrierHooks.name(stack);
		if (name != null) callback.setReturnValue(name);
	}

	@Inject(method = "appendHoverText", at = @At("TAIL"))
	private void littleChemistry$dynamicTooltip(ItemStack stack, Item.TooltipContext context,
			TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag, CallbackInfo callback) {
		if ((Object) this instanceof DynamicItemCarrier) {
			DynamicCarrierHooks.appendHoverText(stack, context, display, builder, flag);
		}
	}
}
