package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiCraftingMenuAccess;
import com.yeyito.littlechemistry.crafting.SharedCraftingContainer;
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
		if (slotIndex >= 0 && slotIndex <= 9
				&& (Object)this instanceof AiCraftingMenuAccess access
				&& access.littleChemistry$getRecipeState() == AiCraftingMenuAccess.GENERATING) {
			callback.cancel();
		}
	}

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$makeRecipe(Player player, int buttonId, CallbackInfoReturnable<Boolean> result) {
		if (buttonId != AiCraftingMenuAccess.MAKE_RECIPE_BUTTON_ID
				|| !((Object)this instanceof AiCraftingMenuAccess access)
				|| !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		SharedCraftingContainer table = access.littleChemistry$getSharedTable();
		if (table == null) return;
		AiCraftingManager manager = AiCraftingManager.active();
		result.setReturnValue(manager != null && manager.requestRecipe(serverPlayer, table));
	}
}
