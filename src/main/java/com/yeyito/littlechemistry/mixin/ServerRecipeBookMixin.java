package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Makes the server recipe catalog, rather than per-player advancements, own recipe visibility. */
@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {
	@Inject(method = "sendInitialRecipeBook", at = @At("RETURN"))
	private void littleChemistry$sendRuntimeRecipes(ServerPlayer player, CallbackInfo callback) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null) manager.sendRecipeBookSnapshot(player);
	}

	@Inject(method = "removeRecipes", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$keepEveryRecipeUnlocked(Collection<RecipeHolder<?>> recipes, ServerPlayer player,
			CallbackInfoReturnable<Integer> result) {
		result.setReturnValue(0);
	}
}
