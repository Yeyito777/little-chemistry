package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.content.DynamicProjectileCarrierHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies stack-aware generated ammunition rules before Minecraft consults the shared carrier item's predicates. */
@Mixin(Player.class)
abstract class PlayerProjectileMixin {
	@Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$generatedAmmunition(ItemStack weapon,
			CallbackInfoReturnable<ItemStack> callback) {
		@Nullable ItemStack selected = DynamicProjectileCarrierHooks.customAmmunition((Player) (Object) this, weapon);
		if (selected != null) callback.setReturnValue(selected);
	}
}
