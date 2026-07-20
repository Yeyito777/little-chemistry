package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$blockLockedGridClicks(int slotIndex, int buttonNum, ContainerInput input,
			Player player, CallbackInfo callback) {
		if (!((Object)this instanceof AiRecipeMenuAccess access)) return;
		if (access.littleChemistry$isProtectedCarrierInteraction(slotIndex, buttonNum, input, player)
				|| (access.littleChemistry$getRecipeState() == AiRecipeMenuAccess.GENERATING
				&& access.littleChemistry$isLockedRecipeSlot(slotIndex))) {
			callback.cancel();
		}
	}

	@Inject(method = "clicked", at = @At("TAIL"))
	private void littleChemistry$reattachMovedPortableTables(int slotIndex, int buttonNum, ContainerInput input,
			Player player, CallbackInfo callback) {
		if (!(player instanceof ServerPlayer serverPlayer)) return;
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager == null || !manager.belongsTo(serverPlayer.level())) return;
		AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
		for (var slot : menu.slots) manager.reconcilePortableStack(slot.getItem(), serverPlayer.level());
		manager.reconcilePortableStack(menu.getCarried(), serverPlayer.level());
	}

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$makeRecipe(Player player, int buttonId, CallbackInfoReturnable<Boolean> result) {
		if (buttonId != AiRecipeMenuAccess.MAKE_RECIPE_BUTTON_ID
				|| !((Object)this instanceof AiRecipeMenuAccess access)
				|| !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		result.setReturnValue(access.littleChemistry$requestRecipe(serverPlayer));
	}
}
